package com.lowenhert.patchmanagement.core.script.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScriptLogDTO {
    private String log;
    private Instant timestamp;
}
