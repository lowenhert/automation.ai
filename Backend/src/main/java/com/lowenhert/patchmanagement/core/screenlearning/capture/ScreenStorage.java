package com.lowenhert.patchmanagement.core.screenlearning.capture;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** Resolves screenshot and metadata files on disk. Files are named by capture UUID only. */
@Slf4j
@Component
public class ScreenStorage {

    private final Path captureDir;

    public ScreenStorage(@Value("${app.screen-learning.capture.output-dir:data/screen-captures}") String captureDir) {
        this.captureDir = Paths.get(captureDir).toAbsolutePath().normalize();
        log.info("Screen captures will be stored in {}", this.captureDir);
    }

    public Path getCaptureDir() {
        return captureDir;
    }

    public Path imagePath(String captureId) {
        return resolve(requireCaptureId(captureId) + ".png");
    }

    public Path metadataPath(String captureId) {
        return resolve(requireCaptureId(captureId) + ".json");
    }

    /** Validates and normalizes a capture id (canonical lowercase UUID). */
    public static String requireCaptureId(String captureId) {
        try {
            String normalized = UUID.fromString(captureId).toString();
            if (!normalized.equalsIgnoreCase(captureId)) throw new IllegalArgumentException();
            return normalized;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Invalid screen id");
        }
    }

    private Path resolve(String fileName) {
        Path file = captureDir.resolve(fileName).normalize();
        if (!file.startsWith(captureDir)) {
            throw new BadRequestException("Invalid screen id");
        }
        return file;
    }
}
