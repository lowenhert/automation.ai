package com.lowenhert.patchmanagement.core.screenlearning.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbCommandExecutor;
import com.lowenhert.patchmanagement.core.screenlearning.adb.ScreenLearningAdbService;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import com.lowenhert.patchmanagement.core.screenlearning.capture.dto.ScreenCaptureResponse;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreenRepository;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenHistoryService;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.ScreenDetailDto;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.ScreenSummaryDto;
import com.lowenhert.patchmanagement.core.screenlearning.process.ProcessRunner;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end against a real device: Java -> capture.py -> adb -> analyzer -> history DB (in-memory H2).
 * Skipped automatically when adb/python are unavailable or no device is online.
 */
@DataJpaTest
@ActiveProfiles("h2")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ScreenCaptureRealDeviceTest {

    @Autowired
    private CapturedScreenRepository repository;
    @Autowired
    private EntityManager entityManager;

    @TempDir
    Path captureDir;

    @Test
    void capturesAnalyzesAndStoresRealScreenshot() throws Exception {
        String adbPath = Optional.ofNullable(System.getenv("ADB_PATH")).orElse("adb");
        String python = Optional.ofNullable(System.getenv("PYTHON_PATH")).orElse("python");
        String tesseract = Optional.ofNullable(System.getenv("TESSERACT_PATH")).orElse("");
        ProcessRunner runner = new ProcessRunner();
        AdbCommandExecutor adb = new AdbCommandExecutor(runner, adbPath, 15000);
        ScreenLearningAdbService adbService = new ScreenLearningAdbService(adb);

        String serial;
        try {
            serial = adbService.listDevices().stream()
                    .filter(d -> "device".equals(d.getState()))
                    .map(AdbDeviceDto::getSerial)
                    .findFirst().orElse(null);
        } catch (RuntimeException e) {
            serial = null;
        }
        assumeTrue(serial != null, "No online ADB device; skipping real-device capture test");

        ObjectMapper mapper = new ObjectMapper();
        ScreenStorage storage = new ScreenStorage(captureDir.toString());
        ScreenHistoryService history = new ScreenHistoryService(repository, storage, mapper);
        ScreenCaptureService service = new ScreenCaptureService(runner, adb, adbService, storage, history, mapper,
                python, "../ai-engine/capture/capture.py", 90000, 15000, tesseract, "eng");

        ScreenCaptureResponse response = service.capture(serial);
        entityManager.flush();
        entityManager.clear();

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("SUCCESS", response.getAnalysisStatus(), response.getAnalysisError());

        // Screenshot is a real PNG on disk, separate from the DB row
        byte[] bytes = Files.readAllBytes(history.imageFile(response.getCaptureId()));
        assertEquals(response.getSizeBytes(), bytes.length);
        assertArrayEquals(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, java.util.Arrays.copyOf(bytes, 4));

        ScreenDetailDto detail = history.get(response.getCaptureId());
        assertEquals(serial, detail.getDeviceSerial());
        assertNotNull(detail.getDeviceType());
        assertEquals(response.getResolution(), detail.getResolution());
        assertEquals(64, detail.getSha256().length());
        assertNotNull(detail.getPhash());
        assertEquals(response.getTextRegionCount(), detail.getTextRegionCount());
        assertEquals(detail.getTextRegionCount(), detail.getTextRegions().size());
        for (ScreenDetailDto.TextRegionDto r : detail.getTextRegions()) {
            assertEquals("OCR_TEXT", r.type());
            assertTrue(r.bbox().x() + r.bbox().width() <= detail.getWidth() + 2, r.toString());
            assertTrue(r.bbox().y() + r.bbox().height() <= detail.getHeight() + 2, r.toString());
        }

        assertEquals(1, history.list(0, 20, serial).totalElements());
        if (!tesseract.isEmpty() && !detail.getTextRegions().isEmpty()) {
            String word = detail.getTextRegions().stream()
                    .map(ScreenDetailDto.TextRegionDto::text)
                    .filter(t -> t.strip().length() >= 3)
                    .findFirst().orElseThrow();
            ScreenSummaryDto hit = history.search(word, 0, 20).content().get(0);
            assertEquals(response.getCaptureId(), hit.getId());
            assertFalse(hit.getMatchedTexts().isEmpty());
        }
    }
}
