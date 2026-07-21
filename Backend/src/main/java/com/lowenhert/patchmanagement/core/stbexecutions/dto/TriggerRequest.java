package com.lowenhert.patchmanagement.core.stbexecutions.dto;

import lombok.*;

import java.util.List;

@Data
public class TriggerRequest {
    private String deviceId;
    private List<String> modules;
}
