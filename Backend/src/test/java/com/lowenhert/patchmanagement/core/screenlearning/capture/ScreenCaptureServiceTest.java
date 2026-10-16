package com.lowenhert.patchmanagement.core.screenlearning.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.config.exception.ServiceUnavailableException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbCommandExecutor;
import com.lowenhert.patchmanagement.core.screenlearning.adb.ScreenLearningAdbService;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import com.lowenhert.patchmanagement.core.screenlearning.capture.dto.ScreenCaptureResponse;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenHistoryService;
import com.lowenhert.patchmanagement.core.screenlearning.process.ProcessRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScreenCaptureServiceTest {

    private static final String SCRIPT = "../ai-engine/capture/capture.py";

    @TempDir
    Path captureDir;

    private ProcessRunner runner;
    private ScreenLearningAdbService adbService;
    private ScreenHistoryService historyService;
    private ScreenStorage storage;
    private ScreenCaptureService service;

    @BeforeEach
    void setUp() {
        runner = mock(ProcessRunner.class);
        adbService = mock(ScreenLearningAdbService.class);
        historyService = mock(ScreenHistoryService.class);
        storage = new ScreenStorage(captureDir.toString());
        AdbCommandExecutor adbExecutor = mock(AdbCommandExecutor.class);
        when(adbExecutor.getAdbPath()).thenReturn("adb");
        when(adbService.requireOnlineDevice("emulator-5554")).thenReturn(
                AdbDeviceDto.builder().serial("emulator-5554").state("device").connectionType("EMULATOR").build());
        service = new ScreenCaptureService(runner, adbExecutor, adbService, storage, historyService, new ObjectMapper(),
                "python", SCRIPT, 60000, 15000, "C:/Tess/tesseract.exe", "eng");
    }

    @Test
    void passesOcrConfigurationToScript() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenReturn(new ProcessRunner.ProcessResult(4, "{\"status\":\"FAILED\"}", ""));
        assertThrows(BadRequestException.class, () -> service.capture("emulator-5554"));
        verify(runner).run(argThat(cmd -> {
            int t = cmd.indexOf("--tesseract-path");
            int l = cmd.indexOf("--ocr-lang");
            return t > 0 && cmd.get(t + 1).equals("C:/Tess/tesseract.exe") && l > 0 && cmd.get(l + 1).equals("eng");
        }), any(), anyLong());
    }

    @Test
    void successfulCaptureReportsAnalysisSummary() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String id = cmd.get(cmd.indexOf("--capture-id") + 1);
            Files.write(captureDir.resolve(id + ".png"), new byte[]{1});
            return new ProcessRunner.ProcessResult(0, "{\"status\":\"SUCCESS\",\"captureId\":\"" + id + "\","
                    + "\"width\":1080,\"height\":2340,\"resolution\":\"1080x2340\",\"timestamp\":\"t\",\"sizeBytes\":1,"
                    + "\"analysis\":{\"status\":\"SUCCESS\",\"ocrStatus\":\"FAILED\",\"textRegionCount\":0,"
                    + "\"ocrError\":{\"code\":\"TESSERACT_NOT_FOUND\",\"message\":\"Tesseract executable not found\"}}}", "");
        });

        ScreenCaptureResponse response = service.capture("emulator-5554");

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("SUCCESS", response.getAnalysisStatus());
        assertEquals("FAILED", response.getOcrStatus());
        assertEquals("Tesseract executable not found", response.getAnalysisError());
        assertEquals("/api/screens/" + response.getCaptureId() + "/analysis", response.getAnalysisUrl());
    }

    @Test
    void missingAnalysisDoesNotFailCapture() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenAnswer(inv -> {
            List<String> cmd = inv.getArgument(0);
            String id = cmd.get(cmd.indexOf("--capture-id") + 1);
            Files.write(captureDir.resolve(id + ".png"), new byte[]{1});
            return new ProcessRunner.ProcessResult(0, "{\"status\":\"SUCCESS\",\"captureId\":\"" + id + "\","
                    + "\"analysis\":{\"status\":\"FAILED\",\"message\":\"Analyzer dependencies missing\"}}", "");
        });

        ScreenCaptureResponse response = service.capture("emulator-5554");

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("FAILED", response.getAnalysisStatus());
        assertNull(response.getAnalysisUrl());
        assertEquals("Analyzer dependencies missing", response.getAnalysisError());
    }

    @Test
    void getAnalysisReadsMetadataAndValidatesId() throws Exception {
        String id = "3f1c2b6e-1a2b-4c3d-8e9f-0a1b2c3d4e5f";
        Files.writeString(captureDir.resolve(id + ".json"), "{\"ocr\":{\"status\":\"SUCCESS\"}}");
        assertEquals("SUCCESS", service.getAnalysis(id).path("ocr").path("status").asText());
        assertThrows(ResourceNotFoundException.class, () -> service.getAnalysis("00000000-0000-4000-8000-000000000000"));
        assertThrows(BadRequestException.class, () -> service.getAnalysis("../application"));
    }

    @Test
    void invalidOcrLangConfigIsRejected() {
        assertThrows(IllegalStateException.class, () -> new ScreenCaptureService(runner, mock(AdbCommandExecutor.class),
                adbService, storage, historyService, new ObjectMapper(), "python", SCRIPT, 60000, 15000, "", "eng;rm"));
    }

    @Test
    void deviceUnavailableMapsToBadRequest() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenReturn(new ProcessRunner.ProcessResult(4,
                "{\"status\":\"FAILED\",\"errorCode\":\"DEVICE_UNAVAILABLE\",\"message\":\"Device emulator-5554 is not available: offline\"}", ""));
        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.capture("emulator-5554"));
        assertTrue(ex.getMessage().contains("not available"));
    }

    @Test
    void scriptFailureMapsToServiceUnavailable() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenReturn(new ProcessRunner.ProcessResult(6,
                "{\"status\":\"FAILED\",\"errorCode\":\"INVALID_PNG\",\"message\":\"Device did not return a valid PNG image\"}", ""));
        assertThrows(ServiceUnavailableException.class, () -> service.capture("emulator-5554"));
    }

    @Test
    void nonJsonOutputIsAFailure() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenReturn(new ProcessRunner.ProcessResult(1, "Traceback ...", "boom"));
        assertThrows(ServiceUnavailableException.class, () -> service.capture("emulator-5554"));
    }

    @Test
    void successWithoutFileOnDiskIsRejected() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenReturn(new ProcessRunner.ProcessResult(0,
                "{\"status\":\"SUCCESS\",\"captureId\":\"x\"}", ""));
        assertThrows(ServiceUnavailableException.class, () -> service.capture("emulator-5554"));
    }

    @Test
    void timeoutMapsToServiceUnavailable() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenThrow(new java.util.concurrent.TimeoutException());
        assertThrows(ServiceUnavailableException.class, () -> service.capture("emulator-5554"));
    }

    @Test
    void offlineDeviceNeverStartsPython() throws Exception {
        when(adbService.requireOnlineDevice("bad")).thenThrow(new BadRequestException("Device is offline"));
        assertThrows(BadRequestException.class, () -> service.capture("bad"));
        verify(runner, never()).run(anyList(), any(), anyLong());
    }

    @Test
    void successfulCaptureIsRecordedInHistory() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenAnswer(inv -> writeSuccess(inv.getArgument(0)));

        ScreenCaptureResponse response = service.capture("emulator-5554");

        verify(historyService).record(argThat(r -> r.getCaptureId().equals(response.getCaptureId())),
                argThat(d -> d.getSerial().equals("emulator-5554")));
    }

    @Test
    void historyFailureRemovesFilesAndFailsRequest() throws Exception {
        when(runner.run(anyList(), any(), anyLong())).thenAnswer(inv -> writeSuccess(inv.getArgument(0)));
        when(historyService.record(any(), any())).thenThrow(new DataAccessResourceFailureException("db down"));

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class, () -> service.capture("emulator-5554"));

        assertTrue(ex.getMessage().contains("history"));
        try (var files = Files.list(captureDir)) {
            assertEquals(0, files.count(), "orphan files must be cleaned up");
        }
    }

    private ProcessRunner.ProcessResult writeSuccess(List<String> cmd) throws Exception {
        String id = cmd.get(cmd.indexOf("--capture-id") + 1);
        Files.write(captureDir.resolve(id + ".png"), new byte[]{1});
        Files.writeString(captureDir.resolve(id + ".json"), "{}");
        return new ProcessRunner.ProcessResult(0, "{\"status\":\"SUCCESS\",\"captureId\":\"" + id + "\",\"width\":1,\"height\":1,"
                + "\"analysis\":{\"status\":\"SUCCESS\",\"ocrStatus\":\"SUCCESS\",\"textRegionCount\":0}}", "");
    }
}
