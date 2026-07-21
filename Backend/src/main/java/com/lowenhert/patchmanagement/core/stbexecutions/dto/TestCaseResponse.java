package com.lowenhert.patchmanagement.core.stbexecutions.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
public class TestCaseResponse {
    private Long id;
    private String moduleName;
    private String testName;
    private String description;
    private String category;
    private Integer totalSteps;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
