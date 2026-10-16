"""Tests for analyzer.py.

OCR tests need Tesseract (set TESSERACT_PATH or put tesseract on PATH); the device test needs an online ADB device.
Run: python -m unittest test_analyzer -v
"""

import hashlib
import json
import os
import shutil
import tempfile
import unittest
import uuid
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

import analyzer
import capture
from test_capture import _online_device

TESSERACT = os.environ.get("TESSERACT_PATH") or shutil.which("tesseract")


def _font(size: int) -> ImageFont.FreeTypeFont:
    for name in ("arial.ttf", "DejaVuSans.ttf", "LiberationSans-Regular.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default(size=size)


def _render_ui(path: Path) -> None:
    """Renders a simple UI-like image: dark text on a light panel and light text on a dark panel."""
    img = Image.new("RGB", (1080, 900), "white")
    draw = ImageDraw.Draw(img)
    draw.text((80, 100), "Settings", fill="black", font=_font(56))
    draw.rectangle((0, 450, 1080, 900), fill=(20, 20, 40))
    draw.text((80, 600), "Network", fill="white", font=_font(56))
    img.save(path)


class InvalidImageTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def _assert_invalid(self, path: Path, code: str):
        with self.assertRaises(analyzer.AnalysisError) as ctx:
            analyzer.analyze_image(path, tesseract_cmd=TESSERACT)
        self.assertEqual(ctx.exception.error_code, code)

    def test_missing_file(self):
        self._assert_invalid(self.dir / "nope.png", "IMAGE_NOT_FOUND")

    def test_empty_file(self):
        p = self.dir / "empty.png"
        p.write_bytes(b"")
        self._assert_invalid(p, "INVALID_IMAGE")

    def test_garbage_bytes(self):
        p = self.dir / "garbage.png"
        p.write_bytes(b"error: device offline\n" * 10)
        self._assert_invalid(p, "INVALID_IMAGE")

    def test_truncated_png(self):
        good = self.dir / "good.png"
        Image.new("RGB", (200, 200), "red").save(good)
        bad = self.dir / "truncated.png"
        bad.write_bytes(good.read_bytes()[:60])
        self._assert_invalid(bad, "INVALID_IMAGE")

    def test_cli_reports_invalid_image(self):
        p = self.dir / "garbage.png"
        p.write_bytes(b"not an image")
        self.assertEqual(analyzer.main([str(p), "--no-save"]), analyzer.EXIT_INVALID_IMAGE)
        self.assertFalse((self.dir / "garbage.json").exists())


class HashAndMetadataTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.image = self.dir / "ui.png"
        _render_ui(self.image)

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def test_ocr_failure_still_returns_hashes(self):
        result = analyzer.analyze_image(self.image, tesseract_cmd=str(self.dir / "no-tesseract.exe"))
        self.assertEqual(result["ocr"]["status"], "FAILED")
        self.assertEqual(result["ocr"]["error"]["code"], "TESSERACT_NOT_FOUND")
        self.assertEqual(result["image"]["width"], 1080)
        self.assertEqual(result["image"]["height"], 900)
        self.assertEqual(result["checksums"]["sha256"], hashlib.sha256(self.image.read_bytes()).hexdigest())
        self.assertEqual(len(result["perceptualHashes"]["phash"]), 16)
        self.assertIn("not verified", result["note"])

    def test_perceptual_hash_is_stable_across_reencoding(self):
        jpeg = self.dir / "ui.jpg"
        Image.open(self.image).convert("RGB").save(jpeg, quality=85)
        a = analyzer.perceptual_hashes(Image.open(self.image))
        b = analyzer.perceptual_hashes(Image.open(jpeg))
        import imagehash
        self.assertLessEqual(imagehash.hex_to_hash(a["phash"]) - imagehash.hex_to_hash(b["phash"]), 4)

    def test_save_metadata_beside_image(self):
        result = analyzer.analyze_image(self.image, tesseract_cmd=str(self.dir / "no-tesseract.exe"))
        path = analyzer.save_metadata(result, self.image)
        self.assertEqual(path, self.dir / "ui.json")
        self.assertEqual(json.loads(path.read_text(encoding="utf-8"))["checksums"], result["checksums"])
        self.assertEqual(sorted(p.name for p in self.dir.iterdir()), ["ui.json", "ui.png"])


class GroupingTest(unittest.TestCase):
    @staticmethod
    def _word(text, x, y, w=100, h=40, conf=90.0):
        return {"text": text, "confidence": conf, "bbox": analyzer._bbox(x, y, w, h), "pass": "p"}

    def test_words_on_same_row_merge_into_one_line(self):
        regions = analyzer._group_lines([self._word("Oct", 180, 256), self._word("Thu,", 60, 256),
                                         self._word("15", 300, 258)])
        self.assertEqual([r["text"] for r in regions], ["Thu, Oct 15"])
        self.assertEqual(regions[0]["bbox"], {"x": 60, "y": 256, "width": 340, "height": 42})

    def test_distant_words_stay_separate(self):
        regions = analyzer._group_lines([self._word("Left", 0, 0), self._word("Right", 900, 0),
                                         self._word("Below", 0, 200)])
        self.assertEqual([r["text"] for r in regions], ["Left", "Right", "Below"])
        self.assertEqual([r["id"] for r in regions], [1, 2, 3])

    def test_dedupe_keeps_most_confident_overlap(self):
        kept = analyzer._dedupe([self._word("Bluetoothypairing)", 218, 1002, 308, 45, 85),
                                 self._word("Bluetooth,", 223, 1004, 180, 38, 95),
                                 self._word("pairing", 410, 1004, 110, 38, 96)])
        self.assertEqual(sorted(w["text"] for w in kept), ["Bluetooth,", "pairing"])

    def test_icon_noise_filtered(self):
        self.assertFalse(analyzer._is_meaningful("@", 90))
        self.assertFalse(analyzer._is_meaningful("©©@", 90))
        self.assertFalse(analyzer._is_meaningful("S", 50))
        self.assertTrue(analyzer._is_meaningful("A", 95))
        self.assertTrue(analyzer._is_meaningful("12:49", 77))


@unittest.skipUnless(TESSERACT, "Tesseract not available")
class OcrTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())
        self.image = self.dir / "ui.png"
        _render_ui(self.image)

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def test_detects_text_of_both_polarities_with_coordinates(self):
        result = analyzer.analyze_image(self.image, tesseract_cmd=TESSERACT)
        ocr = result["ocr"]
        self.assertEqual(ocr["status"], "SUCCESS", ocr["error"])
        by_text = {r["text"]: r for r in ocr["textRegions"]}
        self.assertIn("Settings", by_text)
        self.assertIn("Network", by_text)
        for text, (x, y) in {"Settings": (80, 100), "Network": (80, 600)}.items():
            region = by_text[text]
            self.assertEqual(region["type"], "OCR_TEXT")
            self.assertGreaterEqual(region["confidence"], 50)
            self.assertLess(abs(region["bbox"]["x"] - x), 30, region)
            self.assertLess(abs(region["bbox"]["y"] - y), 40, region)
        self.assertEqual(ocr["textRegionCount"], len(ocr["textRegions"]))

    def test_tesseract_error_is_reported_not_raised(self):
        result = analyzer.analyze_image(self.image, tesseract_cmd=TESSERACT, lang="zzz_missing_lang")
        self.assertEqual(result["ocr"]["status"], "FAILED")
        self.assertEqual(result["ocr"]["error"]["code"], "OCR_FAILED")


@unittest.skipUnless(TESSERACT and _online_device(), "needs Tesseract and an online ADB device")
class RealDeviceAnalysisTest(unittest.TestCase):
    def setUp(self):
        self.dir = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def test_capture_writes_metadata_beside_screenshot(self):
        capture_id = str(uuid.uuid4())
        code = capture.main(["--device-id", _online_device(), "--output-dir", str(self.dir),
                             "--capture-id", capture_id, "--tesseract-path", TESSERACT])
        self.assertEqual(code, capture.EXIT_OK)

        png, meta = self.dir / f"{capture_id}.png", self.dir / f"{capture_id}.json"
        self.assertTrue(png.is_file())
        data = json.loads(meta.read_text(encoding="utf-8"))
        self.assertEqual(data["capture"]["captureId"], capture_id)
        self.assertEqual(data["checksums"]["sha256"], hashlib.sha256(png.read_bytes()).hexdigest())
        with Image.open(png) as img:
            self.assertEqual((data["image"]["width"], data["image"]["height"]), img.size)
        self.assertEqual(data["ocr"]["status"], "SUCCESS", data["ocr"]["error"])
        for region in data["ocr"]["textRegions"]:
            b = region["bbox"]
            self.assertTrue(0 <= b["x"] and b["x"] + b["width"] <= data["image"]["width"] + 2, region)
            self.assertTrue(0 <= b["y"] and b["y"] + b["height"] <= data["image"]["height"] + 2, region)


if __name__ == "__main__":
    unittest.main()
