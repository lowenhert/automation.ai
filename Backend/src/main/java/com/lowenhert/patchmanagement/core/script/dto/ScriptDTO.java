package com.lowenhert.patchmanagement.core.script.dto;

import lombok.Data;

import java.time.LocalDateTime;

import com.lowenhert.patchmanagement.core.script.model.ScriptType;

@Data
public class ScriptDTO {
    private Long id;
    private String name;
    private String description;
    private ScriptType type;
    private String content;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean active = true;
}