package com.lowenhert.patchmanagement.core.screenlearning.history.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScreenSummaryDto {
    private String id;
    private String deviceSerial;
    private String deviceModel;
    private String deviceType;
    private Instant capturedAt;
    private int width;
    private int height;
    private String resolution;
    private String phash;
    private String analysisStatus;
    private String ocrStatus;
    private int textRegionCount;
    private String textPreview;
    private String imageUrl;
    /** Only set for search results: OCR region texts that matched the query. */
    private List<String> matchedTexts;
}
