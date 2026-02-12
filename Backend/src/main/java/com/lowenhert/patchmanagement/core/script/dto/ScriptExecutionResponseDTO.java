package com.lowenhert.patchmanagement.core.script.dto;

import java.time.LocalDateTime;
import com.lowenhert.patchmanagement.core.script.model.ExecutionStatus;
import lombok.Data;

@Data
public class ScriptExecutionResponseDTO {
    private Long id;
    private String scriptName;
    private ExecutionStatus status;
    private LocalDateTime executedAt;
    private Long durationMs;

    // ✅ Add these if you need them in the mapper
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String error;
}
