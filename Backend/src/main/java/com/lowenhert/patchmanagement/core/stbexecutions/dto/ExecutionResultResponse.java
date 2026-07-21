package com.lowenhert.patchmanagement.core.stbexecutions.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
public class ExecutionResultResponse {
    private Long id;
    private Long executionId;
    private Long testCaseId;
    private String moduleName;
    private Integer stepNumber;
    private String stepName;
    private String status;
    private String errorMessage;
    private Long durationMs;
    private String screenshotPath;
    private LocalDateTime timestamp;
}
