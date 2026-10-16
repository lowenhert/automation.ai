"""Static validator for user test scripts. Parses only; never imports or executes the script.

Restricts scripts to the Automation.ai device API so they cannot run shell commands, open files or reach the
network. This is defence in depth: execution is additionally limited to user-approved code, a scrubbed
environment, an isolated working directory and a hard timeout.

Usage: python validate_script.py --language pytest|robot --file PATH
Prints one JSON object: {"valid", "errors": [{line, message}], "warnings", "tests", "actions": [{line, action, detail}]}
"""

from __future__ import annotations

import argparse
import ast
import json
import sys
from pathlib import Path

MAX_BYTES = 200_000

# ── Python / pytest ─────────────────────────────────────────────────────────

ALLOWED_MODULES = {"pytest", "automation_ai", "re", "math", "json", "datetime", "time"}
ALLOWED_AUTOMATION_NAMES = {"Device", "DeviceError", "KEYS"}
FORBIDDEN_NAMES = {
    "eval", "exec", "compile", "open", "__import__", "getattr", "setattr", "delattr", "globals", "locals",
    "vars", "input", "breakpoint", "memoryview", "help", "exit", "quit", "format", "__builtins__", "__loader__",
    "__spec__",
}
FORBIDDEN_ATTRIBUTES = {"format", "format_map", "importorskip", "register_assert_rewrite"}
DEVICE_METHODS = {"press", "sleep", "screenshot", "visible_text", "assert_text_visible", "assert_text_not_visible",
                  "assert_any_text_visible"}


def validate_python(source: str) -> dict:
    result = _result()
    try:
        tree = ast.parse(source, filename="script.py")
    except SyntaxError as exc:
        _error(result, exc.lineno or 1, f"Syntax error: {exc.msg}")
        return _finish(result)

    for node in ast.walk(tree):
        line = getattr(node, "lineno", 1)
        if isinstance(node, ast.Import):
            for alias in node.names:
                if alias.name not in ALLOWED_MODULES:
                    _error(result, line, f"Import of '{alias.name}' is not allowed (allowed: {', '.join(sorted(ALLOWED_MODULES))})")
        elif isinstance(node, ast.ImportFrom):
            if node.level or node.module not in ALLOWED_MODULES:
                _error(result, line, f"Import from '{node.module or '.'}' is not allowed")
            elif node.module == "automation_ai":
                for alias in node.names:
                    if alias.name not in ALLOWED_AUTOMATION_NAMES:
                        _error(result, line, f"'{alias.name}' is not part of the automation_ai API")
            elif any(alias.name == "*" for alias in node.names):
                _error(result, line, "Wildcard imports are not allowed")
        elif isinstance(node, ast.Name):
            if node.id in FORBIDDEN_NAMES or node.id.startswith("__"):
                _error(result, line, f"Use of '{node.id}' is not allowed")
        elif isinstance(node, ast.Attribute):
            if node.attr.startswith("_"):
                _error(result, line, f"Access to private attribute '{node.attr}' is not allowed")
            elif node.attr in FORBIDDEN_ATTRIBUTES:
                _error(result, line, f"'.{node.attr}' is not allowed")
        elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            if node.name.startswith("test"):
                result["tests"].append(node.name)
            for decorator in node.decorator_list:
                _check_string_condition(result, decorator)
        elif isinstance(node, ast.ClassDef):
            if node.keywords:
                _error(result, line, "Class keywords (e.g. metaclass) are not allowed")
        elif isinstance(node, ast.Call):
            _record_device_call(result, node)

    if not result["tests"]:
        _error(result, 1, "No test functions found (define at least one 'def test_...(device):')")
    return _finish(result)


def _check_string_condition(result: dict, decorator: ast.expr) -> None:
    """pytest evaluates string conditions of skipif/xfail with eval()."""
    if not isinstance(decorator, ast.Call):
        return
    name = ast.unparse(decorator.func)
    if name.endswith(("mark.skipif", "mark.xfail")):
        values = list(decorator.args) + [k.value for k in decorator.keywords if k.arg == "condition"]
        if any(isinstance(v, ast.Constant) and isinstance(v.value, str) for v in values):
            _error(result, decorator.lineno, f"String conditions in {name} are evaluated as code and are not allowed")


def _record_device_call(result: dict, node: ast.Call) -> None:
    if isinstance(node.func, ast.Attribute) and node.func.attr in DEVICE_METHODS:
        detail = ""
        if node.args and isinstance(node.args[0], ast.Constant):
            detail = repr(node.args[0].value)
        result["actions"].append({"line": node.lineno, "action": node.func.attr, "detail": detail})


# ── Robot Framework ─────────────────────────────────────────────────────────

ALLOWED_LIBRARIES = {"AutomationAiLibrary", "Collections", "String"}
LIBRARY_KEYWORDS = {"presskey", "waitseconds", "takescreenshot", "getvisibletext", "textshouldbevisible",
                    "textshouldnotbevisible", "anytextshouldbevisible"}
# BuiltIn keywords that evaluate Python expressions or run keywords chosen at runtime
BLOCKED_KEYWORDS = {
    "evaluate", "shouldbetrue", "shouldnotbetrue", "callmethod", "importlibrary", "importresource",
    "importvariables", "setlibrarysearchorder", "setvariableif", "passexecutionif", "returnfromkeywordif",
    "exitforloopif", "continueforloopif", "waituntilkeywordsucceeds", "repeatkeyword", "runkeyword",
    "runkeywordif", "runkeywordunless", "runkeywords", "runkeywordandexpecterror", "runkeywordandignoreerror",
    "runkeywordandreturn", "runkeywordandreturnif", "runkeywordandreturnstatus", "runkeywordandcontinueonfailure",
    "runkeywordandwarnonfailure", "runkeywordifalltestspassed", "runkeywordifanytestsfailed",
    "runkeywordiftestfailed", "runkeywordiftestpassed", "runkeywordiftimeoutoccurred",
    "runkeywordifallcriticaltestspassed", "runkeywordifanycriticaltestsfailed", "setsuitemetadata",
}


def _normalize_keyword(name: str) -> str:
    name = (name or "").strip()
    if "." in name:
        prefix, _, rest = name.rpartition(".")
        if prefix.replace(" ", "").lower() in {"builtin", "automationailibrary", "collections", "string"}:
            name = rest
    return name.lower().replace(" ", "").replace("_", "")


def validate_robot(source: str, path: Path) -> dict:
    from robot.api import Token, get_model
    from robot.parsing.model.blocks import TestCase
    from robot.parsing.model.statements import Statement

    result = _result()
    model = get_model(str(path), data_only=False)

    for node in ast.walk(model):
        if isinstance(node, TestCase):
            result["tests"].append(node.name)
        if not isinstance(node, Statement):
            continue
        line = node.lineno or 1
        for token in node.tokens:
            if token.type == Token.ERROR:
                _error(result, token.lineno or line, f"Syntax error: {token.error}")
            if token.type == Token.COMMENT:
                continue
            value = token.value or ""
            if any(_unescaped(value, m) for m in ("${{", "@{{", "&{{")):
                _error(result, token.lineno or line, "Inline Python evaluation (${{ }}) is not allowed")
            if _unescaped(value, "%{"):
                _error(result, token.lineno or line, "Environment variables (%{...}) are not allowed")

        kind = node.type
        if kind == Token.LIBRARY:
            if node.name not in ALLOWED_LIBRARIES:
                _error(result, line, f"Library '{node.name}' is not allowed (allowed: {', '.join(sorted(ALLOWED_LIBRARIES))})")
            if node.args or node.alias:
                _error(result, line, "Library arguments and aliases are not allowed")
        elif kind in (Token.RESOURCE, Token.VARIABLES):
            _error(result, line, "Resource and variable file imports are not allowed")
        elif kind == Token.KEYWORD:
            _check_robot_keyword(result, node.keyword, line, node.args)
        elif kind in (Token.SETUP, Token.TEARDOWN, Token.SUITE_SETUP, Token.SUITE_TEARDOWN,
                      Token.TEST_SETUP, Token.TEST_TEARDOWN):
            if node.name:
                _check_robot_keyword(result, node.name, line, node.args)
        elif kind in (Token.TEMPLATE, Token.TEST_TEMPLATE):
            if node.value:
                _check_robot_keyword(result, node.value, line, ())
        elif kind in (Token.IF, Token.ELSE_IF, Token.INLINE_IF, Token.WHILE):
            _error(result, line, f"{kind} conditions are evaluated as Python and are not allowed")
        elif kind == Token.FOR and node.flavor == "IN RANGE":
            for value in node.values:
                if not value.strip().lstrip("-").isdigit():
                    _error(result, line, "FOR IN RANGE values must be integer literals")

    if not result["tests"]:
        _error(result, 1, "No test cases found (add a '*** Test Cases ***' section)")
    return _finish(result)


def _unescaped(value: str, marker: str) -> bool:
    """True if marker occurs in value without being escaped by an odd number of backslashes."""
    start = value.find(marker)
    while start != -1:
        backslashes = 0
        i = start - 1
        while i >= 0 and value[i] == "\\":
            backslashes += 1
            i -= 1
        if backslashes % 2 == 0:
            return True
        start = value.find(marker, start + 1)
    return False


def _check_robot_keyword(result: dict, keyword: str, line: int, args) -> None:
    normalized = _normalize_keyword(keyword)
    if normalized in BLOCKED_KEYWORDS or normalized.startswith("runkeyword"):
        _error(result, line, f"Keyword '{keyword}' is not allowed (it evaluates code or runs keywords dynamically)")
    elif normalized in LIBRARY_KEYWORDS:
        result["actions"].append({"line": line, "action": keyword.strip(), "detail": ", ".join(args or ())[:120]})


# ── Shared ──────────────────────────────────────────────────────────────────

def _result() -> dict:
    return {"valid": False, "errors": [], "warnings": [], "tests": [], "actions": []}


def _error(result: dict, line: int, message: str) -> None:
    result["errors"].append({"line": int(line), "message": message})


def _finish(result: dict) -> dict:
    result["errors"].sort(key=lambda e: e["line"])
    result["valid"] = not result["errors"]
    if not result["actions"] and result["valid"]:
        result["warnings"].append({"line": 1, "message": "Script does not interact with the device"})
    return result


def validate(language: str, path: Path) -> dict:
    data = path.read_bytes()
    if len(data) > MAX_BYTES:
        result = _result()
        _error(result, 1, f"Script exceeds {MAX_BYTES} bytes")
        return _finish(result)
    source = data.decode("utf-8", errors="strict")
    if "\x00" in source:
        result = _result()
        _error(result, 1, "Script contains NUL characters")
        return _finish(result)
    return validate_python(source) if language == "pytest" else validate_robot(source, path)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate an Automation.ai test script without executing it.")
    parser.add_argument("--language", required=True, choices=["pytest", "robot"])
    parser.add_argument("--file", required=True)
    args = parser.parse_args(argv)
    try:
        result = validate(args.language, Path(args.file))
    except (OSError, UnicodeDecodeError) as exc:
        result = _finish(_result() | {"errors": [{"line": 1, "message": f"Cannot read script: {exc}"}]})
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
