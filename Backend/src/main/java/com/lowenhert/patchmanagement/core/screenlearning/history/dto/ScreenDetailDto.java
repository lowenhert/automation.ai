package com.lowenhert.patchmanagement.core.screenlearning.history.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenDetailDto {
    public static final String REGION_NOTE =
            "Text regions are OCR detections only; they are not verified interactive or clickable UI elements.";

    private String id;
    private String deviceSerial;
    private String deviceModel;
    private String deviceType;
    private Instant capturedAt;
    private int width;
    private int height;
    private String resolution;
    private Long sizeBytes;
    private String sha256;
    private String phash;
    private String dhash;
    private String ahash;
    private String analysisStatus;
    private String ocrStatus;
    private String analysisError;
    private int textRegionCount;
    private String imageUrl;
    private List<TextRegionDto> textRegions;
    private String note;

    public record TextRegionDto(int index, String type, String text, Double confidence, BoundingBox bbox) {
    }

    public record BoundingBox(int x, int y, int width, int height) {
    }
}
