package com.lowenhert.patchmanagement.core.stbexecutions.dto;

import lombok.*;

@Data
public class CreateTestCaseRequest {
    private String moduleName;
    private String testName;
    private String description;
    private String category;
    private Integer totalSteps;
}
