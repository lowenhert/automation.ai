package com.lowenhert.patchmanagement.core.stbexecutions.dto;

import lombok.*;

import java.util.List;

@Data
public class CreateExecutionRequest {
    private String executionName;
    private Integer stbNo;
    private String boxName;
    private List<Long> testCaseIds;
    private String triggeredBy;
    private String notes;
}
