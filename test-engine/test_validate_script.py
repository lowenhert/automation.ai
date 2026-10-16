"""Tests for validate_script.py and the automation_ai helpers (no device needed).

Run from test-engine/: python -m unittest test_validate_script -v
"""

import tempfile
import unittest
from pathlib import Path

import validate_script
from automation_ai.device import Device, DeviceError, text_matches

GOOD_PYTEST = '''
import pytest
from automation_ai import Device


def test_open_settings(device):
    device.press("HOME")
    device.press("SELECT")
    device.assert_text_visible("Network & internet", timeout=10)
    device.screenshot("settings")
'''

GOOD_ROBOT = '''*** Settings ***
Library    AutomationAiLibrary

*** Test Cases ***
Open Settings
    Press Key    HOME
    Press Key    SELECT
    Text Should Be Visible    Network & internet    timeout=10
    FOR    ${i}    IN RANGE    3
        Press Key    DPAD_DOWN
    END
    Take Screenshot    settings
'''


def check(language: str, source: str) -> dict:
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / ("script.py" if language == "pytest" else "script.robot")
        path.write_text(source, encoding="utf-8")
        return validate_script.validate(language, path)


def messages(result: dict) -> str:
    return " | ".join(e["message"] for e in result["errors"])


class PytestValidationTest(unittest.TestCase):
    def test_valid_script_lists_tests_and_actions(self):
        r = check("pytest", GOOD_PYTEST)
        self.assertTrue(r["valid"], messages(r))
        self.assertEqual(r["tests"], ["test_open_settings"])
        self.assertEqual([a["action"] for a in r["actions"]],
                         ["press", "press", "assert_text_visible", "screenshot"])
        self.assertEqual(r["actions"][0]["detail"], "'HOME'")

    def test_rejects_dangerous_constructs(self):
        cases = {
            "import os\ndef test_x(device): os.system('reboot')": "Import of 'os'",
            "import subprocess\ndef test_x(device): pass": "Import of 'subprocess'",
            "from os import system\ndef test_x(device): pass": "Import from 'os'",
            "def test_x(device): eval('1')": "'eval'",
            "def test_x(device): exec('x=1')": "'exec'",
            "def test_x(device): open('C:/secret.txt')": "'open'",
            "def test_x(device): __import__('os')": "'__import__'",
            "def test_x(device): getattr(device, '_adb')('shell', 'reboot')": "'getattr'",
            "def test_x(device): device._adb('shell', 'reboot')": "private attribute '_adb'",
            "def test_x(device): ().__class__.__bases__[0].__subclasses__()": "private attribute '__class__'",
            "def test_x(device): '{0.__class__}'.format(device)": "'.format'",
            "import pytest\n@pytest.mark.skipif(\"__import__('os').system('x')\", reason='r')\ndef test_x(device): pass":
                "String conditions",
            "import pytest\ndef test_x(device): pytest.importorskip('os')": "'.importorskip'",
            "from . import x\ndef test_x(device): pass": "Import from '.'",
            "from automation_ai.device import Device\ndef test_x(device): pass": "Import from 'automation_ai.device'",
            "from automation_ai import _something\ndef test_x(device): pass": "not part of the automation_ai API",
            "class M(type): pass\nclass X(metaclass=M): pass\ndef test_x(device): pass": "Class keywords",
            "def helper(): pass": "No test functions",
            "def test_x(device)\n    pass": "Syntax error",
        }
        for source, expected in cases.items():
            with self.subTest(source=source):
                r = check("pytest", source)
                self.assertFalse(r["valid"])
                self.assertIn(expected, messages(r))

    def test_size_limit(self):
        r = check("pytest", "# x\n" * 60_000 + "def test_x(device): pass\n")
        self.assertIn("exceeds", messages(r))


class RobotValidationTest(unittest.TestCase):
    def test_valid_script(self):
        r = check("robot", GOOD_ROBOT)
        self.assertTrue(r["valid"], messages(r))
        self.assertEqual(r["tests"], ["Open Settings"])
        self.assertIn("Press Key", [a["action"] for a in r["actions"]])

    def test_escaped_markers_and_comments_are_literal(self):
        source = ("*** Test Cases ***\nT\n    # %{PATH} ${{x}} in a comment\n"
                  "    Text Should Be Visible    \\%{PATH} \\${{1+1}}\n")
        r = check("robot", source)
        self.assertTrue(r["valid"], messages(r))

    def test_rejects_dangerous_constructs(self):
        cases = {
            "*** Settings ***\nLibrary    OperatingSystem\n*** Test Cases ***\nT\n    Log    x\n": "Library 'OperatingSystem'",
            "*** Settings ***\nLibrary    Process\n*** Test Cases ***\nT\n    Run Process    cmd\n": "Library 'Process'",
            "*** Test Cases ***\nT\n    Evaluate    __import__('os').system('x')\n": "Keyword 'Evaluate'",
            "*** Test Cases ***\nT\n    BuiltIn.Evaluate    1\n": "Keyword 'BuiltIn.Evaluate'",
            "*** Test Cases ***\nT\n    Should Be True    __import__('os')\n": "Keyword 'Should Be True'",
            "*** Test Cases ***\nT\n    Run Keyword    Log    x\n": "Keyword 'Run Keyword'",
            "*** Test Cases ***\nT\n    Log    ${{__import__('os').getcwd()}}\n": "Inline Python",
            "*** Test Cases ***\nT\n    Log    %{PATH}\n": "Environment variables",
            "*** Test Cases ***\nT\n    Log    \\\\%{PATH}\n": "Environment variables",
            "*** Test Cases ***\nT\n    Log    \\\\${{1+1}}\n": "Inline Python",
            "*** Test Cases ***\nT\n    IF    __import__('os')\n        Log    x\n    END\n": "IF conditions",
            "*** Test Cases ***\nT\n    WHILE    True\n        Log    x\n    END\n": "WHILE conditions",
            "*** Test Cases ***\nT\n    FOR    ${i}    IN RANGE    ${{9**9}}\n        Log    x\n    END\n": "Inline Python",
            "*** Settings ***\nResource    other.resource\n*** Test Cases ***\nT\n    Log    x\n": "Resource and variable",
            "*** Settings ***\nLibrary    AutomationAiLibrary    arg\n*** Test Cases ***\nT\n    Log    x\n": "arguments and aliases",
            "*** Settings ***\nTest Setup    Evaluate    1\n*** Test Cases ***\nT\n    Log    x\n": "Keyword 'Evaluate'",
            "*** Keywords ***\nK\n    Log    x\n": "No test cases",
        }
        for source, expected in cases.items():
            with self.subTest(source=source):
                r = check("robot", source)
                self.assertFalse(r["valid"])
                self.assertIn(expected, messages(r))


class DeviceHelperTest(unittest.TestCase):
    def test_text_matching(self):
        lines = ["Network & internet", "Wi-Fi", "Device preferences"]
        self.assertTrue(text_matches(lines, "network & internet"))
        self.assertTrue(text_matches(lines, "Wi Fi"))
        self.assertFalse(text_matches(lines, "Bluetooth"))
        self.assertFalse(text_matches(lines, ""))

    def test_device_rejects_invalid_serial_and_keys(self):
        with self.assertRaises(DeviceError):
            Device(serial="-s; reboot", artifact_dir=tempfile.mkdtemp())
        device = Device(serial="emulator-5554", adb_path="definitely-not-adb", artifact_dir=tempfile.mkdtemp())
        with self.assertRaises(DeviceError):
            device.press("POWER")
        with self.assertRaises(DeviceError):
            device.press("HOME", times=500)
        with self.assertRaises(DeviceError):
            device.sleep(600)
        with self.assertRaises(DeviceError) as ctx:
            device.press("HOME")
        self.assertIn("adb not available", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
