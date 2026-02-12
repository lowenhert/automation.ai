package com.lowenhert.patchmanagement.core.script.dto;

import com.lowenhert.patchmanagement.core.script.model.ScriptType;

import lombok.Data;

@Data
public class ScriptRequestDTO {
    private String name;
    private String description;
    private ScriptType type;
    private String content;
}