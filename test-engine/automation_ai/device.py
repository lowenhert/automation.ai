"""Device API for approved Automation.ai test scripts.

This is the only way test scripts interact with a device: allow-listed remote keys, screenshots and OCR-based
text assertions. adb is invoked with fixed argument lists (no shell).

Configuration comes from environment variables set by the backend for each execution:
    AUTOMATION_DEVICE_ID      adb serial of the selected device
    AUTOMATION_ADB_PATH       adb executable (default: adb)
    AUTOMATION_ARTIFACT_DIR   where screenshots and events.jsonl are written
    AUTOMATION_CAPTURE_DIR    ai-engine/capture (for the OCR analyzer)
    TESSERACT_PATH            Tesseract executable (optional)
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

KEYS = {
    "DPAD_UP": "KEYCODE_DPAD_UP",
    "DPAD_DOWN": "KEYCODE_DPAD_DOWN",
    "DPAD_LEFT": "KEYCODE_DPAD_LEFT",
    "DPAD_RIGHT": "KEYCODE_DPAD_RIGHT",
    "SELECT": "KEYCODE_DPAD_CENTER",
    "BACK": "KEYCODE_BACK",
    "HOME": "KEYCODE_HOME",
}

_SERIAL = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:\-]{0,127}$")
_NAME = re.compile(r"[^A-Za-z0-9_-]+")
_NON_ALNUM = re.compile(r"[^\w]+", re.UNICODE)
_PNG = b"\x89PNG\r\n\x1a\n"

MAX_SLEEP_SECONDS = 30.0
MAX_ASSERT_TIMEOUT = 120.0
MAX_PRESS_REPEAT = 20
MAX_SCREENSHOTS = 200
ADB_TIMEOUT = 20.0
POLL_INTERVAL = 1.0


class DeviceError(RuntimeError):
    """The device could not perform an action (offline, adb failure, invalid argument)."""


def _normalize(text: str) -> str:
    return _NON_ALNUM.sub(" ", (text or "").lower()).replace("_", " ").strip()


def text_matches(visible: list[str], expected: str) -> bool:
    """Case/punctuation-insensitive match of an expected text against OCR lines (same rule as the planner)."""
    target = _normalize(expected)
    if not target:
        return False
    for line in visible:
        known = _normalize(line)
        if known and (known == target or (len(target) >= 3 and target in known) or (len(known) >= 4 and known in target)):
            return True
    return False


class Device:
    """A connected Android emulator or STB, controlled through adb."""

    def __init__(self, serial: str | None = None, adb_path: str | None = None, artifact_dir: str | None = None):
        serial = serial or os.environ.get("AUTOMATION_DEVICE_ID", "")
        if not _SERIAL.match(serial):
            raise DeviceError("No valid device selected (AUTOMATION_DEVICE_ID)")
        self.serial = serial
        self._adb_path = adb_path or os.environ.get("AUTOMATION_ADB_PATH") or "adb"
        self._artifacts = Path(artifact_dir or os.environ.get("AUTOMATION_ARTIFACT_DIR") or "artifacts").resolve()
        self._artifacts.mkdir(parents=True, exist_ok=True)
        self._events = self._artifacts / "events.jsonl"
        self._screenshots = 0
        self._analyzer = None

    # ── Actions ─────────────────────────────────────────────────────────────

    def press(self, key: str, times: int = 1) -> None:
        """Press an allow-listed remote key: DPAD_UP/DOWN/LEFT/RIGHT, SELECT, BACK, HOME."""
        if key not in KEYS:
            raise DeviceError(f"Unsupported key {key!r}; allowed: {', '.join(KEYS)}")
        if not isinstance(times, int) or not 1 <= times <= MAX_PRESS_REPEAT:
            raise DeviceError(f"times must be an integer between 1 and {MAX_PRESS_REPEAT}")
        for _ in range(times):
            self._adb("shell", "input", "keyevent", KEYS[key])
        self._event("press", key=key, times=times)

    def sleep(self, seconds: float) -> None:
        if not isinstance(seconds, (int, float)) or not 0 <= seconds <= MAX_SLEEP_SECONDS:
            raise DeviceError(f"sleep seconds must be between 0 and {MAX_SLEEP_SECONDS}")
        time.sleep(seconds)

    def screenshot(self, name: str = "screen") -> str:
        """Saves a PNG of the current screen into the execution artifacts and returns its path."""
        if self._screenshots >= MAX_SCREENSHOTS:
            raise DeviceError(f"Screenshot limit ({MAX_SCREENSHOTS}) reached")
        data = self._adb("exec-out", "screencap", "-p", binary=True)
        if not data.startswith(_PNG):
            raise DeviceError("Device did not return a PNG screenshot")
        self._screenshots += 1
        safe = (_NAME.sub("_", name).strip("_") or "screen")[:40]
        path = self._artifacts / f"{self._screenshots:03d}_{safe}.png"
        path.write_bytes(data)
        self._event("screenshot", file=path.name)
        return str(path)

    # ── OCR-based checks ────────────────────────────────────────────────────

    def visible_text(self) -> list[str]:
        """Text currently visible on screen, read by OCR from a fresh screenshot."""
        data = self._adb("exec-out", "screencap", "-p", binary=True)
        if not data.startswith(_PNG):
            raise DeviceError("Device did not return a PNG screenshot")
        ocr = self._ocr(data)
        if ocr["status"] != "SUCCESS":
            raise DeviceError(f"OCR failed: {(ocr.get('error') or {}).get('message', 'unknown error')}")
        return [r["text"] for r in ocr["textRegions"]]

    def assert_text_visible(self, text: str, timeout: float = 10) -> None:
        self._wait_for(lambda lines: text_matches(lines, text), timeout, f"Text {text!r} not visible")

    def assert_text_not_visible(self, text: str, timeout: float = 10) -> None:
        self._wait_for(lambda lines: not text_matches(lines, text), timeout, f"Text {text!r} still visible")

    def assert_any_text_visible(self, texts: list[str], timeout: float = 10) -> None:
        if not texts:
            raise DeviceError("texts must not be empty")
        self._wait_for(lambda lines: any(text_matches(lines, t) for t in texts), timeout,
                       f"None of {list(texts)!r} visible")

    # ── Internals (not available to scripts; validation forbids '_' attributes) ──

    def _wait_for(self, condition, timeout: float, failure: str) -> None:
        if not isinstance(timeout, (int, float)) or not 0 < timeout <= MAX_ASSERT_TIMEOUT:
            raise DeviceError(f"timeout must be between 0 and {MAX_ASSERT_TIMEOUT} seconds")
        deadline = time.monotonic() + timeout
        lines: list[str] = []
        while True:
            lines = self.visible_text()
            if condition(lines):
                self._event("assert", result="pass", detail=failure.replace("not visible", "visible"))
                return
            if time.monotonic() >= deadline:
                break
            time.sleep(POLL_INTERVAL)
        shot = self.screenshot("assert_failure")
        self._event("assert", result="fail", detail=failure, screenshot=Path(shot).name)
        raise AssertionError(f"{failure} after {timeout:g}s. Visible text: {lines[:15]!r}")

    def _adb(self, *args: str, binary: bool = False):
        try:
            result = subprocess.run([self._adb_path, "-s", self.serial, *args],
                                    capture_output=True, timeout=ADB_TIMEOUT, check=False)
        except subprocess.TimeoutExpired as exc:
            raise DeviceError(f"adb {' '.join(args[:2])} timed out") from exc
        except OSError as exc:
            raise DeviceError(f"adb not available: {exc}") from exc
        if result.returncode != 0:
            message = (result.stderr or result.stdout).decode("utf-8", "replace").strip()[:300]
            raise DeviceError(f"adb {' '.join(args[:2])} failed: {message or 'exit ' + str(result.returncode)}")
        return result.stdout if binary else result.stdout.decode("utf-8", "replace")

    def _ocr(self, png: bytes) -> dict:
        if self._analyzer is None:
            capture_dir = os.environ.get("AUTOMATION_CAPTURE_DIR")
            if capture_dir and capture_dir not in sys.path:
                sys.path.insert(0, capture_dir)
            try:
                import analyzer  # ai-engine/capture/analyzer.py
            except ImportError as exc:
                raise DeviceError(f"OCR analyzer unavailable: {exc}") from exc
            self._analyzer = analyzer
        return self._analyzer.run_ocr(png, os.environ.get("TESSERACT_PATH") or None, "eng",
                                      self._analyzer.DEFAULT_PSM, self._analyzer.DEFAULT_MIN_CONFIDENCE, 30.0)

    def _event(self, kind: str, **fields) -> None:
        record = {"time": time.time(), "event": kind, **fields}
        with self._events.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(record) + "\n")
