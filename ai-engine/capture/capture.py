"""Capture a PNG screenshot from a connected Android device / emulator / STB over ADB,
then (unless --skip-analysis) run analyzer.py and save <captureId>.json beside the PNG.

Prints exactly one JSON object to stdout describing the result and exits with:
    0 success, 2 invalid arguments, 3 adb not found, 4 device not available,
    5 timeout, 6 capture failed, 7 file system error.
Analysis/OCR failures do not change the exit code; see the "analysis" field.

Example:
    python capture.py --device-id emulator-5554 --output-dir ./captures --tesseract-path "C:/Program Files/Tesseract-OCR/tesseract.exe"
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import uuid
from datetime import datetime, timezone
from pathlib import Path

EXIT_OK = 0
EXIT_INVALID_ARGS = 2
EXIT_ADB_NOT_FOUND = 3
EXIT_DEVICE_UNAVAILABLE = 4
EXIT_TIMEOUT = 5
EXIT_CAPTURE_FAILED = 6
EXIT_IO_ERROR = 7

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
# First char must not be '-' so the serial can never be parsed as an adb option
DEVICE_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:\-]{0,127}$")
CAPTURE_ID_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
DEVICE_TMP_DIR = "/data/local/tmp"


class CaptureError(Exception):
    def __init__(self, exit_code: int, error_code: str, message: str):
        super().__init__(message)
        self.exit_code = exit_code
        self.error_code = error_code
        self.message = message


def parse_png_size(data: bytes) -> tuple[int, int]:
    """Returns (width, height) from the PNG IHDR chunk; raises CaptureError if data is not a PNG."""
    if len(data) < 24 or not data.startswith(PNG_SIGNATURE) or data[12:16] != b"IHDR":
        raise CaptureError(EXIT_CAPTURE_FAILED, "INVALID_PNG", "Device did not return a valid PNG image")
    width, height = struct.unpack(">II", data[16:24])
    if width == 0 or height == 0:
        raise CaptureError(EXIT_CAPTURE_FAILED, "INVALID_PNG", "Captured PNG has zero size")
    return width, height


def resolve_adb(adb_path: str) -> str:
    resolved = shutil.which(adb_path)
    if resolved is None and Path(adb_path).is_file():
        resolved = str(Path(adb_path).resolve())
    if resolved is None:
        raise CaptureError(EXIT_ADB_NOT_FOUND, "ADB_NOT_FOUND", f"ADB executable not found: {adb_path}")
    return resolved


def run_adb(adb: str, args: list[str], timeout: float) -> subprocess.CompletedProcess:
    try:
        return subprocess.run([adb, *args], capture_output=True, timeout=timeout, check=False)
    except subprocess.TimeoutExpired as exc:
        raise CaptureError(EXIT_TIMEOUT, "TIMEOUT", f"adb {' '.join(args)} timed out after {timeout:.0f}s") from exc
    except OSError as exc:
        raise CaptureError(EXIT_ADB_NOT_FOUND, "ADB_NOT_FOUND", f"Unable to run adb: {exc}") from exc


def _text(raw: bytes) -> str:
    return raw.decode("utf-8", errors="replace").strip()


def ensure_device_online(adb: str, device_id: str, timeout: float) -> None:
    result = run_adb(adb, ["-s", device_id, "get-state"], timeout)
    state = _text(result.stdout)
    if result.returncode != 0 or state != "device":
        detail = _text(result.stderr) or state or "unknown state"
        raise CaptureError(EXIT_DEVICE_UNAVAILABLE, "DEVICE_UNAVAILABLE",
                           f"Device {device_id} is not available: {detail}")


def capture_exec_out(adb: str, device_id: str, timeout: float) -> bytes | None:
    """Fast path: stream the PNG directly (Android 5+). Returns None if the output is not a PNG."""
    result = run_adb(adb, ["-s", device_id, "exec-out", "screencap", "-p"], timeout)
    if result.returncode == 0 and result.stdout.startswith(PNG_SIGNATURE):
        return result.stdout
    return None


def capture_via_pull(adb: str, device_id: str, capture_id: str, timeout: float, work_dir: Path) -> bytes:
    """Fallback for older STB firmware without exec-out: screencap to a device file, then pull it."""
    remote = f"{DEVICE_TMP_DIR}/sl_{capture_id}.png"
    local = work_dir / f".pull_{capture_id}.png"
    try:
        shot = run_adb(adb, ["-s", device_id, "shell", "screencap", "-p", remote], timeout)
        if shot.returncode != 0:
            raise CaptureError(EXIT_CAPTURE_FAILED, "SCREENCAP_FAILED",
                               f"screencap failed: {_text(shot.stderr) or _text(shot.stdout)}")
        pull = run_adb(adb, ["-s", device_id, "pull", remote, str(local)], timeout)
        if pull.returncode != 0 or not local.is_file():
            raise CaptureError(EXIT_CAPTURE_FAILED, "PULL_FAILED",
                               f"adb pull failed: {_text(pull.stderr) or _text(pull.stdout)}")
        return local.read_bytes()
    finally:
        try:
            run_adb(adb, ["-s", device_id, "shell", "rm", "-f", remote], timeout)
        except CaptureError:
            pass
        local.unlink(missing_ok=True)


def write_atomically(data: bytes, target: Path) -> None:
    fd, tmp_name = tempfile.mkstemp(prefix=".capture_", suffix=".tmp", dir=target.parent)
    try:
        with os.fdopen(fd, "wb") as fh:
            fh.write(data)
        os.replace(tmp_name, target)
    except BaseException:
        Path(tmp_name).unlink(missing_ok=True)
        raise


def capture(adb_path: str, device_id: str, output_dir: str, timeout: float,
            capture_id: str | None = None) -> dict:
    if not DEVICE_ID_PATTERN.match(device_id or ""):
        raise CaptureError(EXIT_INVALID_ARGS, "INVALID_DEVICE_ID", "Invalid device id")
    capture_id = capture_id or str(uuid.uuid4())
    if not CAPTURE_ID_PATTERN.match(capture_id):
        raise CaptureError(EXIT_INVALID_ARGS, "INVALID_CAPTURE_ID", "Capture id must be a lowercase UUID")
    if timeout <= 0:
        raise CaptureError(EXIT_INVALID_ARGS, "INVALID_TIMEOUT", "Timeout must be positive")

    out_dir = Path(output_dir).resolve()
    try:
        out_dir.mkdir(parents=True, exist_ok=True)
    except OSError as exc:
        raise CaptureError(EXIT_IO_ERROR, "OUTPUT_DIR_ERROR", f"Cannot create output directory: {exc}") from exc

    adb = resolve_adb(adb_path)
    ensure_device_online(adb, device_id, timeout)

    data = capture_exec_out(adb, device_id, timeout)
    if data is None:
        data = capture_via_pull(adb, device_id, capture_id, timeout, out_dir)
    width, height = parse_png_size(data)

    file_name = f"{capture_id}.png"
    target = out_dir / file_name
    try:
        write_atomically(data, target)
    except OSError as exc:
        raise CaptureError(EXIT_IO_ERROR, "WRITE_FAILED", f"Cannot write screenshot: {exc}") from exc

    return {
        "status": "SUCCESS",
        "captureId": capture_id,
        "deviceId": device_id,
        "fileName": file_name,
        "imagePath": str(target),
        "width": width,
        "height": height,
        "resolution": f"{width}x{height}",
        "sizeBytes": len(data),
        "timestamp": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
    }


def analyze_capture(result: dict, tesseract_path: str | None, ocr_lang: str) -> dict:
    """Runs analyzer.py on a captured image and saves <captureId>.json beside it.

    Analysis problems never fail the capture; they are reported in the returned summary.
    """
    try:
        import analyzer  # imported lazily so capture still works without the OCR dependencies
    except ImportError as exc:
        return {"status": "FAILED", "errorCode": "ANALYZER_UNAVAILABLE", "message": f"Analyzer dependencies missing: {exc}"}

    try:
        analysis = analyzer.analyze_image(result["imagePath"], tesseract_cmd=tesseract_path, lang=ocr_lang)
        analysis["capture"] = {"captureId": result["captureId"], "deviceId": result["deviceId"],
                               "capturedAt": result["timestamp"]}
        metadata_file = analyzer.save_metadata(analysis, result["imagePath"])
    except analyzer.AnalysisError as exc:
        return {"status": "FAILED", "errorCode": exc.error_code, "message": exc.message}
    except OSError as exc:
        return {"status": "FAILED", "errorCode": "METADATA_WRITE_FAILED", "message": str(exc)}

    ocr = analysis["ocr"]
    return {
        "status": "SUCCESS",
        "metadataFile": metadata_file.name,
        "sha256": analysis["checksums"]["sha256"],
        "phash": analysis["perceptualHashes"]["phash"],
        "ocrStatus": ocr["status"],
        "textRegionCount": ocr["textRegionCount"],
        "ocrError": ocr["error"],
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Capture a PNG screenshot from an ADB device.")
    parser.add_argument("--adb-path", default=os.environ.get("ADB_PATH", "adb"),
                        help="adb executable name or path (default: $ADB_PATH or 'adb')")
    parser.add_argument("--device-id", required=True, help="ADB serial, e.g. emulator-5554 or 192.168.1.10:5555")
    parser.add_argument("--output-dir", required=True, help="Directory where the PNG is written")
    parser.add_argument("--timeout", type=float, default=20.0, help="Per-adb-command timeout in seconds")
    parser.add_argument("--capture-id", default=None, help="Lowercase UUID used as the file name (generated if omitted)")
    parser.add_argument("--tesseract-path", default=os.environ.get("TESSERACT_PATH") or None,
                        help="Tesseract executable (default: $TESSERACT_PATH, else 'tesseract' on PATH)")
    parser.add_argument("--ocr-lang", default="eng", help="Tesseract language(s), e.g. eng or eng+hin")
    parser.add_argument("--skip-analysis", action="store_true", help="Capture only; do not run the analyzer")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        result = capture(args.adb_path, args.device_id, args.output_dir, args.timeout, args.capture_id)
    except CaptureError as exc:
        print(json.dumps({"status": "FAILED", "errorCode": exc.error_code, "message": exc.message,
                          "deviceId": args.device_id}))
        return exc.exit_code
    if not args.skip_analysis:
        result["analysis"] = analyze_capture(result, args.tesseract_path, args.ocr_lang)
    print(json.dumps(result))
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
