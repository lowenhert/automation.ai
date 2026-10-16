package com.lowenhert.patchmanagement.core.screenlearning.capture;

import com.fasterxml.jackson.databind.JsonNode;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Captures device screenshots by invoking ai-engine/capture/capture.py. */
@Slf4j
@Service
public class ScreenCaptureService {

    // Exit codes defined by capture.py
    static final int EXIT_INVALID_ARGS = 2;
    static final int EXIT_DEVICE_UNAVAILABLE = 4;

    private static final Pattern OCR_LANG = Pattern.compile("^[A-Za-z_]{2,32}(\\+[A-Za-z_]{2,32}){0,4}$");

    private final ProcessRunner processRunner;
    private final AdbCommandExecutor adbExecutor;
    private final ScreenLearningAdbService adbService;
    private final ScreenStorage storage;
    private final ScreenHistoryService historyService;
    private final ObjectMapper objectMapper;
    private final String pythonPath;
    private final Path scriptPath;
    private final long processTimeoutMs;
    private final long adbTimeoutSeconds;
    private final String tesseractPath;
    private final String ocrLang;

    public ScreenCaptureService(ProcessRunner processRunner,
                                AdbCommandExecutor adbExecutor,
                                ScreenLearningAdbService adbService,
                                ScreenStorage storage,
                                ScreenHistoryService historyService,
                                ObjectMapper objectMapper,
                                @Value("${app.screen-learning.python-path:python}") String pythonPath,
                                @Value("${app.screen-learning.capture.script:../ai-engine/capture/capture.py}") String scriptPath,
                                @Value("${app.screen-learning.capture.timeout-ms:90000}") long processTimeoutMs,
                                @Value("${app.adb.timeout-ms:15000}") long adbTimeoutMs,
                                @Value("${app.screen-learning.ocr.tesseract-path:}") String tesseractPath,
                                @Value("${app.screen-learning.ocr.lang:eng}") String ocrLang) {
        this.processRunner = processRunner;
        this.adbExecutor = adbExecutor;
        this.adbService = adbService;
        this.storage = storage;
        this.historyService = historyService;
        this.objectMapper = objectMapper;
        this.pythonPath = pythonPath.trim();
        this.scriptPath = Paths.get(scriptPath).toAbsolutePath().normalize();
        this.processTimeoutMs = processTimeoutMs;
        this.adbTimeoutSeconds = Math.max(1, adbTimeoutMs / 1000);
        this.tesseractPath = tesseractPath == null ? "" : tesseractPath.trim();
        this.ocrLang = ocrLang == null ? "eng" : ocrLang.trim();
        if (!OCR_LANG.matcher(this.ocrLang).matches()) {
            throw new IllegalStateException("Invalid app.screen-learning.ocr.lang: " + this.ocrLang);
        }

        if (!Files.isRegularFile(this.scriptPath)) {
            log.warn("Screen capture script not found at {}. Set app.screen-learning.capture.script", this.scriptPath);
        }
    }

    public ScreenCaptureResponse capture(String deviceId) {
        AdbDeviceDto device = adbService.requireOnlineDevice(deviceId);
        String serial = device.getSerial();
        if (!Files.isRegularFile(scriptPath)) {
            throw new ServiceUnavailableException("Screen capture script is not configured");
        }

        String captureId = UUID.randomUUID().toString();
        List<String> command = new ArrayList<>(List.of(
                pythonPath, scriptPath.toString(),
                "--adb-path", adbExecutor.getAdbPath(),
                "--device-id", serial,
                "--output-dir", storage.getCaptureDir().toString(),
                "--timeout", String.valueOf(adbTimeoutSeconds),
                "--capture-id", captureId,
                "--ocr-lang", ocrLang));
        if (!tesseractPath.isEmpty()) {
            command.add("--tesseract-path");
            command.add(tesseractPath);
        }

        log.info("Capturing screen from {} (captureId={})", serial, captureId);
        ProcessRunner.ProcessResult result;
        try {
            result = processRunner.run(command, scriptPath.getParent().toFile(), processTimeoutMs);
        } catch (IOException e) {
            log.error("Unable to start python '{}': {}", pythonPath, e.getMessage());
            throw new ServiceUnavailableException("Python not available. Check 'app.screen-learning.python-path'.");
        } catch (TimeoutException e) {
            throw new ServiceUnavailableException("Screen capture timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Screen capture was interrupted");
        }

        JsonNode json = parseLastJsonLine(result.stdout());
        if (result.exitCode() != 0 || json == null || !"SUCCESS".equals(json.path("status").asText())) {
            String message = json != null ? json.path("message").asText("Screen capture failed") : "Screen capture failed";
            log.warn("Capture failed for {} exit={} stdout={} stderr={}", serial, result.exitCode(), result.stdout(), result.stderr());
            if (result.exitCode() == EXIT_INVALID_ARGS || result.exitCode() == EXIT_DEVICE_UNAVAILABLE) {
                throw new BadRequestException(message);
            }
            throw new ServiceUnavailableException(message);
        }

        // Trust only our own captureId for the file location, not paths echoed by the script
        Path image = storage.imagePath(captureId);
        if (!Files.isRegularFile(image) || !captureId.equals(json.path("captureId").asText())) {
            throw new ServiceUnavailableException("Screen capture did not produce an image");
        }

        JsonNode analysis = json.path("analysis");
        boolean analysisOk = "SUCCESS".equals(analysis.path("status").asText());
        if (!analysisOk) {
            log.warn("Analysis failed for capture {}: {}", captureId, analysis.path("message").asText("no analysis result"));
        }
        String analysisError = analysisOk
                ? textOrNull(analysis.path("ocrError").path("message"))
                : analysis.path("message").asText("Analysis did not run");

        ScreenCaptureResponse response = ScreenCaptureResponse.builder()
                .captureId(captureId)
                .deviceId(serial)
                .imageUrl("/api/screens/" + captureId + "/image")
                .fileName(image.getFileName().toString())
                .timestamp(json.path("timestamp").asText())
                .width(json.path("width").asInt())
                .height(json.path("height").asInt())
                .resolution(json.path("resolution").asText())
                .sizeBytes(json.path("sizeBytes").asLong())
                .status("SUCCESS")
                .analysisStatus(analysisOk ? "SUCCESS" : "FAILED")
                .analysisUrl(analysisOk ? "/api/screens/" + captureId + "/analysis" : null)
                .ocrStatus(analysisOk ? analysis.path("ocrStatus").asText() : null)
                .textRegionCount(analysis.path("textRegionCount").asInt(0))
                .analysisError(analysisError)
                .build();

        try {
            historyService.record(response, device);
        } catch (DataAccessException e) {
            log.error("Failed to store screen {} in history: {}", captureId, e.getMessage());
            deleteQuietly(image);
            deleteQuietly(storage.metadataPath(captureId));
            throw new ServiceUnavailableException("Screen was captured but could not be saved to history");
        }
        return response;
    }

    /** Returns the raw analyzer metadata JSON saved beside the screenshot. */
    public JsonNode getAnalysis(String captureId) {
        Path metadata = storage.metadataPath(captureId);
        if (!Files.isRegularFile(metadata)) {
            throw new ResourceNotFoundException("Analysis not found for capture: " + captureId);
        }
        try {
            return objectMapper.readTree(metadata.toFile());
        } catch (IOException e) {
            log.error("Unreadable analysis file {}: {}", metadata, e.getMessage());
            throw new ServiceUnavailableException("Analysis metadata is unreadable");
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", file, e.getMessage());
        }
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private JsonNode parseLastJsonLine(String stdout) {
        if (stdout == null || stdout.isBlank()) return null;
        List<String> lines = stdout.lines().filter(l -> !l.isBlank()).toList();
        try {
            return objectMapper.readTree(lines.get(lines.size() - 1));
        } catch (IOException e) {
            return null;
        }
    }
}
