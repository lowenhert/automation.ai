package com.lowenhert.patchmanagement.core.script.mapper;

import org.springframework.stereotype.Component;
import com.lowenhert.patchmanagement.core.script.dto.ScriptExecutionResponseDTO;
import com.lowenhert.patchmanagement.core.script.model.ScriptExecution;

@Component
public class ScriptExecutionMapper {

    public ScriptExecutionResponseDTO toDto(ScriptExecution entity) {
        if (entity == null) {
            return null;
        }

        ScriptExecutionResponseDTO dto = new ScriptExecutionResponseDTO();
        
        if (entity.getScript() != null) {
            dto.setScriptName(entity.getScript().getName());
        }
        dto.setStartTime(entity.getStartedAt());
        dto.setEndTime(entity.getFinishedAt());
        dto.setId(entity.getId());
        dto.setStatus(entity.getStatus());
        dto.setError(entity.getError());
        dto.setExecutedAt(entity.getExecutedAt());

        return dto;
    }
}
