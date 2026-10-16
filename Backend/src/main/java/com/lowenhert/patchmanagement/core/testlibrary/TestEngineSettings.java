package com.lowenhert.patchmanagement.core.testlibrary;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Locations and limits for validating and executing approved test scripts. */
@Slf4j
@Getter
@Component
public class TestEngineSettings {

    private final String pythonPath;
    private final Path testEngineDir;
    private final Path validatorScript;
    private final Path captureDir;
    private final Path executionsDir;
    private final String adbPath;
    private final String tesseractPath;
    private final long validationTimeoutMs;
    private final int defaultTimeoutSeconds;
    private final int maxTimeoutSeconds;
    private final long maxLogBytes;

    public TestEngineSettings(@Value("${app.screen-learning.python-path:python}") String pythonPath,
                              @Value("${app.test-engine.dir:../test-engine}") String testEngineDir,
                              @Value("${app.screen-learning.capture.script:../ai-engine/capture/capture.py}") String captureScript,
                              @Value("${app.test-engine.executions-dir:data/executions}") String executionsDir,
                              @Value("${app.adb.path:adb}") String adbPath,
                              @Value("${app.screen-learning.ocr.tesseract-path:}") String tesseractPath,
                              @Value("${app.test-engine.validation-timeout-ms:30000}") long validationTimeoutMs,
                              @Value("${app.test-engine.default-timeout-seconds:600}") int defaultTimeoutSeconds,
                              @Value("${app.test-engine.max-timeout-seconds:1800}") int maxTimeoutSeconds,
                              @Value("${app.test-engine.max-log-bytes:5242880}") long maxLogBytes) {
        this.pythonPath = pythonPath.trim();
        this.testEngineDir = Paths.get(testEngineDir).toAbsolutePath().normalize();
        this.validatorScript = this.testEngineDir.resolve("validate_script.py");
        Path capture = Paths.get(captureScript).toAbsolutePath().normalize().getParent();
        this.captureDir = capture == null ? Paths.get(".").toAbsolutePath() : capture;
        this.executionsDir = Paths.get(executionsDir).toAbsolutePath().normalize();
        this.adbPath = adbPath.trim();
        this.tesseractPath = tesseractPath == null ? "" : tesseractPath.trim();
        this.validationTimeoutMs = validationTimeoutMs;
        this.maxTimeoutSeconds = Math.max(30, maxTimeoutSeconds);
        this.defaultTimeoutSeconds = Math.min(Math.max(30, defaultTimeoutSeconds), this.maxTimeoutSeconds);
        this.maxLogBytes = Math.max(64 * 1024, maxLogBytes);
        if (!Files.isRegularFile(validatorScript)) {
            log.warn("Test engine not found at {}. Set app.test-engine.dir", this.testEngineDir);
        }
    }
}
