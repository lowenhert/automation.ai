"""Tests for capture.py. The integration test runs only when a real ADB device is online.

Run: python -m unittest test_capture -v
Optionally set SL_TEST_DEVICE to choose the device serial.
"""

import os
import shutil
import struct
import subprocess
import tempfile
import unittest
import uuid
import zlib
from pathlib import Path

import capture


def _png(width: int, height: int) -> bytes:
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    chunk = b"IHDR" + ihdr
    return capture.PNG_SIGNATURE + struct.pack(">I", len(ihdr)) + chunk + struct.pack(">I", zlib.crc32(chunk))


def _online_device() -> str | None:
    adb = shutil.which(os.environ.get("ADB_PATH", "adb"))
    if adb is None:
        return None
    wanted = os.environ.get("SL_TEST_DEVICE")
    try:
        out = subprocess.run([adb, "devices"], capture_output=True, text=True, timeout=15).stdout
    except (OSError, subprocess.TimeoutExpired):
        return None
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device" and (wanted is None or parts[0] == wanted):
            return parts[0]
    return None


class ParsePngSizeTest(unittest.TestCase):
    def test_reads_dimensions(self):
        self.assertEqual(capture.parse_png_size(_png(1920, 1080)), (1920, 1080))

    def test_rejects_non_png(self):
        with self.assertRaises(capture.CaptureError) as ctx:
            capture.parse_png_size(b"error: device offline")
        self.assertEqual(ctx.exception.error_code, "INVALID_PNG")


class ValidationTest(unittest.TestCase):
    def setUp(self):
        self.out = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.out, ignore_errors=True)

    def test_rejects_option_like_device_id(self):
        for bad in ["-s", "--help", "a b", "a;rm", "a&b", ""]:
            with self.assertRaises(capture.CaptureError) as ctx:
                capture.capture("adb", bad, self.out, 5)
            self.assertEqual(ctx.exception.exit_code, capture.EXIT_INVALID_ARGS, bad)

    def test_rejects_non_uuid_capture_id(self):
        with self.assertRaises(capture.CaptureError) as ctx:
            capture.capture("adb", "emulator-5554", self.out, 5, capture_id="../../evil")
        self.assertEqual(ctx.exception.error_code, "INVALID_CAPTURE_ID")

    def test_missing_adb(self):
        with self.assertRaises(capture.CaptureError) as ctx:
            capture.capture("definitely-not-adb-123", "emulator-5554", self.out, 5)
        self.assertEqual(ctx.exception.exit_code, capture.EXIT_ADB_NOT_FOUND)

    def test_main_prints_failure_json(self):
        code = capture.main(["--adb-path", "definitely-not-adb-123", "--device-id", "emulator-5554",
                             "--output-dir", self.out])
        self.assertEqual(code, capture.EXIT_ADB_NOT_FOUND)


@unittest.skipUnless(_online_device(), "no online ADB device")
class RealDeviceCaptureTest(unittest.TestCase):
    def setUp(self):
        self.out = tempfile.mkdtemp()
        self.device = _online_device()
        self.adb = os.environ.get("ADB_PATH", "adb")

    def tearDown(self):
        shutil.rmtree(self.out, ignore_errors=True)

    def test_captures_png(self):
        capture_id = str(uuid.uuid4())
        result = capture.capture(self.adb, self.device, self.out, 30, capture_id)

        self.assertEqual(result["status"], "SUCCESS")
        self.assertEqual(result["captureId"], capture_id)
        self.assertEqual(result["deviceId"], self.device)
        path = Path(result["imagePath"])
        self.assertEqual(path.name, f"{capture_id}.png")
        data = path.read_bytes()
        self.assertEqual(capture.parse_png_size(data), (result["width"], result["height"]))
        self.assertEqual(result["resolution"], f"{result['width']}x{result['height']}")
        self.assertEqual(len(data), result["sizeBytes"])
        self.assertEqual([p.name for p in Path(self.out).iterdir()], [path.name], "no temp files left behind")

    def test_unknown_device(self):
        with self.assertRaises(capture.CaptureError) as ctx:
            capture.capture(self.adb, "no-such-device-123", self.out, 15)
        self.assertEqual(ctx.exception.exit_code, capture.EXIT_DEVICE_UNAVAILABLE)


if __name__ == "__main__":
    unittest.main()
