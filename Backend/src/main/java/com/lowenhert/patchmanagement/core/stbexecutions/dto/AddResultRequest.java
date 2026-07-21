package com.lowenhert.patchmanagement.core.stbexecutions.dto;

import lombok.*;

@Data
public class AddResultRequest {
    private Long executionId;
    private Long testCaseId;
    private String moduleName;
    private Integer stepNumber;
    private String stepName;
    private String status;
    private String errorMessage;
    private Long durationMs;
    private String screenshotPath;
}
