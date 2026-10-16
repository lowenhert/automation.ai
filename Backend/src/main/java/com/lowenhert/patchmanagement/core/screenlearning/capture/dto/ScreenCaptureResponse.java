package com.lowenhert.patchmanagement.core.screenlearning.capture.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenCaptureResponse {
    private String captureId;
    private String deviceId;
    /** API path that serves the PNG, relative to the server root. */
    private String imageUrl;
    private String fileName;
    private String timestamp;
    private int width;
    private int height;
    private String resolution;
    private long sizeBytes;
    private String status;
    /** SUCCESS or FAILED; a failed analysis does not fail the capture. */
    private String analysisStatus;
    private String analysisUrl;
    private String ocrStatus;
    private int textRegionCount;
    private String analysisError;
}
