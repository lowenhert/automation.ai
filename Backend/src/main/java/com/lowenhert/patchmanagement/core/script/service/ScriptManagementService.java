package com.lowenhert.patchmanagement.core.script.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowenhert.patchmanagement.core.script.dto.ScriptDTO;
import com.lowenhert.patchmanagement.core.script.model.Script;
import com.lowenhert.patchmanagement.core.script.repository.ScriptRepository;
import com.lowenhert.patchmanagement.core.user.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScriptManagementService {
    private final ScriptRepository scriptRepository;
    private final UserService userService;

    @Transactional
    public ScriptDTO createScript(ScriptDTO scriptDTO) {
        Script script = new Script();
        mapDtoToEntity(scriptDTO, script);
        script.setCreatedBy(userService.getCurrentUser());
        Script savedScript = scriptRepository.save(script);
        return mapEntityToDto(savedScript);
    }

    @Transactional
    public ScriptDTO updateScript(Long id, ScriptDTO scriptDTO) {
        Script script = scriptRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Script not found"));
        mapDtoToEntity(scriptDTO, script);
        Script updatedScript = scriptRepository.save(script);
        return mapEntityToDto(updatedScript);
    }

    public ScriptDTO getScriptById(Long id) {
        return scriptRepository.findById(id)
                .map(this::mapEntityToDto)
                .orElseThrow(() -> new RuntimeException("Script not found"));
    }
    
    public List<ScriptDTO> getScripts() {
        return scriptRepository.findAll()
                .stream()
                .map(this::mapEntityToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteScript(Long id) {
        scriptRepository.deleteById(id);
    }

    private ScriptDTO mapEntityToDto(Script script) {
        ScriptDTO dto = new ScriptDTO();
        dto.setId(script.getId());
        dto.setName(script.getName());
        dto.setDescription(script.getDescription());
        dto.setType(script.getType());
        dto.setContent(script.getContent());
        dto.setActive(script.isActive());
        dto.setCreatedAt(script.getCreatedAt());
        dto.setUpdatedAt(script.getUpdatedAt());
        if (script.getCreatedBy() != null) {
            dto.setCreatedBy(script.getCreatedBy().getId());
        }
        return dto;
    }

    private void mapDtoToEntity(ScriptDTO dto, Script script) {
        script.setName(dto.getName());
        script.setDescription(dto.getDescription());
        script.setType(dto.getType());
        script.setContent(dto.getContent());
        script.setActive(dto.isActive());
    }
}