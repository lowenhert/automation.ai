"""Analyze a screenshot: OCR text regions (Tesseract), image size, perceptual hashes and SHA-256.

OCR text regions are text detections only. They are NOT verified interactive / clickable UI elements.

Usage as a library:
    from analyzer import analyze_image, save_metadata
    result = analyze_image("shot.png", tesseract_cmd=r"C:/Program Files/Tesseract-OCR/tesseract.exe")
    save_metadata(result, "shot.png")          # writes shot.json beside the image

Usage as a CLI (prints JSON to stdout; exit 0 ok, 2 invalid args, 8 invalid image, 7 file error):
    python analyzer.py shot.png --tesseract-path "C:/Program Files/Tesseract-OCR/tesseract.exe"
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

import cv2
import imagehash
import numpy as np
import pytesseract
from PIL import Image, UnidentifiedImageError

SCHEMA_VERSION = 1
REGION_NOTE = ("Text regions are OCR detections only; they are not verified interactive or clickable UI elements.")
DEFAULT_LANG = "eng"
# PSM 11 = sparse text, which suits UI screenshots with scattered labels
DEFAULT_PSM = 11
DEFAULT_OCR_TIMEOUT = 30.0
DEFAULT_MIN_CONFIDENCE = 50.0
SINGLE_LETTER_MIN_CONFIDENCE = 80.0
# A word mostly covered by more confident words is a duplicate read from the other pass
DUPLICATE_COVERAGE = 0.5
# Max horizontal gap between words of one line, in multiples of the text height
LINE_MAX_GAP = 2.0
# Upscaling improves Tesseract accuracy on small UI text; skipped for already-large images
OCR_UPSCALE = 2.0
OCR_UPSCALE_MAX_SIDE = 2560
MAX_IMAGE_BYTES = 50 * 1024 * 1024

EXIT_OK = 0
EXIT_INVALID_ARGS = 2
EXIT_IO_ERROR = 7
EXIT_INVALID_IMAGE = 8


class AnalysisError(Exception):
    def __init__(self, error_code: str, message: str):
        super().__init__(message)
        self.error_code = error_code
        self.message = message


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _bbox(left: int, top: int, width: int, height: int) -> dict:
    return {"x": int(left), "y": int(top), "width": int(width), "height": int(height)}


def load_image(path: Path) -> tuple[bytes, Image.Image]:
    """Reads and validates the image; raises AnalysisError for missing, oversized or undecodable files."""
    if not path.is_file():
        raise AnalysisError("IMAGE_NOT_FOUND", f"Image not found: {path}")
    size = path.stat().st_size
    if size == 0 or size > MAX_IMAGE_BYTES:
        raise AnalysisError("INVALID_IMAGE", f"Image size {size} bytes is outside the allowed range")
    data = path.read_bytes()
    try:
        with Image.open(io.BytesIO(data)) as probe:
            probe.verify()
        image = Image.open(io.BytesIO(data))
        image.load()
    except (UnidentifiedImageError, OSError, SyntaxError, Image.DecompressionBombError) as exc:
        raise AnalysisError("INVALID_IMAGE", f"Not a valid image: {exc}") from exc
    if image.width == 0 or image.height == 0:
        raise AnalysisError("INVALID_IMAGE", "Image has zero size")
    return data, image


def perceptual_hashes(image: Image.Image) -> dict:
    return {
        "phash": str(imagehash.phash(image)),
        "dhash": str(imagehash.dhash(image)),
        "ahash": str(imagehash.average_hash(image)),
    }


def _ocr_passes(data: bytes) -> tuple[list[tuple[str, Image.Image]], float]:
    """Returns binarized variants for dark-on-light and light-on-dark text, plus the upscale factor used."""
    array = cv2.imdecode(np.frombuffer(data, dtype=np.uint8), cv2.IMREAD_COLOR)
    if array is None:
        raise AnalysisError("INVALID_IMAGE", "OpenCV could not decode the image")
    gray = cv2.cvtColor(array, cv2.COLOR_BGR2GRAY)
    scale = OCR_UPSCALE if max(gray.shape) <= OCR_UPSCALE_MAX_SIDE else 1.0
    if scale != 1.0:
        gray = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)

    def binarize(img: np.ndarray) -> Image.Image:
        return Image.fromarray(cv2.adaptiveThreshold(
            img, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 51, 15))

    return [("dark_on_light", binarize(gray)), ("light_on_dark", binarize(255 - gray))], scale


def _is_meaningful(text: str, confidence: float) -> bool:
    alnum = sum(c.isalnum() for c in text)
    if alnum == 0 or alnum / len(text) < 0.5:
        return False
    # Lone letters are usually icon glyphs misread as text
    return not (len(text) == 1 and text.isalpha() and confidence < SINGLE_LETTER_MIN_CONFIDENCE)


def _extract_words(ocr: dict, scale: float, min_confidence: float, pass_name: str) -> list[dict]:
    words = []
    for i, raw in enumerate(ocr["text"]):
        text = (raw or "").strip()
        try:
            conf = float(ocr["conf"][i])
        except (TypeError, ValueError):
            continue
        if not text or conf < min_confidence or not _is_meaningful(text, conf):
            continue
        words.append({
            "text": text,
            "confidence": round(conf, 2),
            "bbox": _bbox(round(ocr["left"][i] / scale), round(ocr["top"][i] / scale),
                          max(1, round(ocr["width"][i] / scale)), max(1, round(ocr["height"][i] / scale))),
            "pass": pass_name,
        })
    return words


def _intersection(a: dict, b: dict) -> int:
    x1, y1 = max(a["x"], b["x"]), max(a["y"], b["y"])
    x2 = min(a["x"] + a["width"], b["x"] + b["width"])
    y2 = min(a["y"] + a["height"], b["y"] + b["height"])
    return max(0, x2 - x1) * max(0, y2 - y1)


def _dedupe(words: list[dict]) -> list[dict]:
    """The two polarity passes often read the same text; keep the most confident, non-overlapping detections."""
    kept: list[dict] = []
    for word in sorted(words, key=lambda w: w["confidence"], reverse=True):
        area = word["bbox"]["width"] * word["bbox"]["height"]
        covered = sum(_intersection(word["bbox"], k["bbox"]) for k in kept)
        if covered < area * DUPLICATE_COVERAGE:
            kept.append(word)
    return kept


def _group_lines(words: list[dict]) -> list[dict]:
    """Groups words into lines by vertical alignment and horizontal proximity."""
    lines: list[list[dict]] = []
    # Left-to-right so a word always extends the line whose right edge it is next to
    for word in sorted(words, key=lambda w: (w["bbox"]["x"], w["bbox"]["y"])):
        b = word["bbox"]
        cy = b["y"] + b["height"] / 2
        for line in lines:
            last = line[-1]["bbox"]
            height = max(b["height"], last["height"])
            same_row = abs((last["y"] + last["height"] / 2) - cy) <= height * 0.5
            gap = b["x"] - (last["x"] + last["width"])
            if same_row and -height * 0.5 <= gap <= height * LINE_MAX_GAP:
                line.append(word)
                break
        else:
            lines.append([word])

    regions = []
    for words_in_line in lines:
        x1 = min(w["bbox"]["x"] for w in words_in_line)
        y1 = min(w["bbox"]["y"] for w in words_in_line)
        x2 = max(w["bbox"]["x"] + w["bbox"]["width"] for w in words_in_line)
        y2 = max(w["bbox"]["y"] + w["bbox"]["height"] for w in words_in_line)
        regions.append({
            "type": "OCR_TEXT",
            "text": " ".join(w["text"] for w in words_in_line),
            "confidence": round(sum(w["confidence"] for w in words_in_line) / len(words_in_line), 2),
            "bbox": _bbox(x1, y1, x2 - x1, y2 - y1),
            "words": words_in_line,
        })
    regions.sort(key=lambda r: (r["bbox"]["y"], r["bbox"]["x"]))
    for idx, region in enumerate(regions, start=1):
        region["id"] = idx
    return regions


def run_ocr(data: bytes, tesseract_cmd: str | None, lang: str, psm: int,
            min_confidence: float, timeout: float) -> dict:
    """Runs Tesseract; never raises for OCR problems — returns status FAILED with an error instead."""
    result = {"status": "FAILED", "engine": "tesseract", "engineVersion": None, "language": lang, "psm": psm,
              "minConfidence": min_confidence, "scale": None, "durationMs": 0, "textRegionCount": 0,
              "fullText": "", "textRegions": [], "error": None}
    if tesseract_cmd:
        pytesseract.pytesseract.tesseract_cmd = tesseract_cmd

    started = time.monotonic()
    try:
        result["engineVersion"] = str(pytesseract.get_tesseract_version())
        passes, scale = _ocr_passes(data)
        result["scale"] = scale
        words: list[dict] = []
        for pass_name, image in passes:
            ocr = pytesseract.image_to_data(image, lang=lang, config=f"--oem 3 --psm {psm}",
                                            output_type=pytesseract.Output.DICT, timeout=timeout)
            words.extend(_extract_words(ocr, scale, min_confidence, pass_name))
        regions = _group_lines(_dedupe(words))
        result.update(status="SUCCESS", textRegions=regions, textRegionCount=len(regions),
                      fullText="\n".join(r["text"] for r in regions))
    except pytesseract.TesseractNotFoundError:
        result["error"] = {"code": "TESSERACT_NOT_FOUND",
                           "message": "Tesseract executable not found. Set --tesseract-path or TESSERACT_PATH."}
    except pytesseract.TesseractError as exc:
        result["error"] = {"code": "OCR_FAILED", "message": f"Tesseract error: {exc.message or exc}"}
    except RuntimeError as exc:
        # pytesseract raises RuntimeError when its timeout expires
        code = "OCR_TIMEOUT" if "timeout" in str(exc).lower() else "OCR_FAILED"
        result["error"] = {"code": code, "message": str(exc)}
    except AnalysisError as exc:
        result["error"] = {"code": exc.error_code, "message": exc.message}
    finally:
        result["durationMs"] = int((time.monotonic() - started) * 1000)
    return result


def analyze_image(image_path: str | os.PathLike, tesseract_cmd: str | None = None, lang: str = DEFAULT_LANG,
                  psm: int = DEFAULT_PSM, min_confidence: float = DEFAULT_MIN_CONFIDENCE,
                  ocr_timeout: float = DEFAULT_OCR_TIMEOUT) -> dict:
    """Analyzes an image. Raises AnalysisError if the image is invalid; OCR failures are reported in the result."""
    path = Path(image_path)
    data, image = load_image(path)
    with image:
        info = {"fileName": path.name, "width": image.width, "height": image.height,
                "format": image.format, "mode": image.mode, "sizeBytes": len(data)}
        hashes = perceptual_hashes(image)

    return {
        "schemaVersion": SCHEMA_VERSION,
        "analyzedAt": _utc_now(),
        "image": info,
        "checksums": {"sha256": hashlib.sha256(data).hexdigest()},
        "perceptualHashes": hashes,
        "ocr": run_ocr(data, tesseract_cmd, lang, psm, min_confidence, ocr_timeout),
        "note": REGION_NOTE,
    }


def metadata_path_for(image_path: str | os.PathLike) -> Path:
    return Path(image_path).with_suffix(".json")


def save_metadata(analysis: dict, image_path: str | os.PathLike) -> Path:
    """Writes the analysis as <image name>.json beside the image (atomic replace)."""
    target = metadata_path_for(image_path)
    fd, tmp = tempfile.mkstemp(prefix=".analysis_", suffix=".tmp", dir=target.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            json.dump(analysis, fh, ensure_ascii=False, indent=2)
        os.replace(tmp, target)
    except BaseException:
        Path(tmp).unlink(missing_ok=True)
        raise
    return target


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Analyze a screenshot (OCR, hashes, checksums).")
    parser.add_argument("image", help="Path to the image file")
    parser.add_argument("--tesseract-path", default=os.environ.get("TESSERACT_PATH") or None,
                        help="Tesseract executable (default: $TESSERACT_PATH, else 'tesseract' on PATH)")
    parser.add_argument("--lang", default=DEFAULT_LANG, help="Tesseract language(s), e.g. eng or eng+hin")
    parser.add_argument("--psm", type=int, default=DEFAULT_PSM, help="Tesseract page segmentation mode")
    parser.add_argument("--min-confidence", type=float, default=DEFAULT_MIN_CONFIDENCE,
                        help="Drop words below this confidence (0-100)")
    parser.add_argument("--ocr-timeout", type=float, default=DEFAULT_OCR_TIMEOUT, help="OCR timeout in seconds")
    parser.add_argument("--no-save", action="store_true", help="Do not write the JSON metadata file")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if not 0 <= args.psm <= 13 or not 0 <= args.min_confidence <= 100 or args.ocr_timeout <= 0:
        print(json.dumps({"status": "FAILED", "errorCode": "INVALID_ARGS", "message": "Invalid OCR options"}))
        return EXIT_INVALID_ARGS
    try:
        analysis = analyze_image(args.image, args.tesseract_path, args.lang, args.psm,
                                 args.min_confidence, args.ocr_timeout)
        if not args.no_save:
            analysis["metadataFile"] = save_metadata(analysis, args.image).name
    except AnalysisError as exc:
        print(json.dumps({"status": "FAILED", "errorCode": exc.error_code, "message": exc.message}))
        return EXIT_INVALID_IMAGE
    except OSError as exc:
        print(json.dumps({"status": "FAILED", "errorCode": "WRITE_FAILED", "message": str(exc)}))
        return EXIT_IO_ERROR
    print(json.dumps(analysis, ensure_ascii=False))
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
