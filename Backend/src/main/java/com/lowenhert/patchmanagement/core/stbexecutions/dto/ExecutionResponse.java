package com.lowenhert.patchmanagement.core.stbexecutions.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExecutionResponse {
    private Long id;
    private String executionName;
    private Integer stbNo;
    private String boxName;
    private String status;
    private Integer totalTests;
    private Integer passed;
    private Integer failed;
    private Integer skipped;
    private String triggeredBy;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationSeconds;
    private String notes;
    private LocalDateTime createdAt;
    private List<ExecutionResultResponse> results;
}
