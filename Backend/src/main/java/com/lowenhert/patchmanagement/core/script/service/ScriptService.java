package com.lowenhert.patchmanagement.core.script.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.lowenhert.patchmanagement.core.script.dto.ScriptRequestDTO;
import com.lowenhert.patchmanagement.core.script.model.Script;
import com.lowenhert.patchmanagement.core.user.User;

public interface ScriptService {
    Script createScript(ScriptRequestDTO dto, User creator);
    Page<Script> getAllScripts(Pageable pageable);
    Script getScriptById(Long id);
    void deleteScript(Long id);
}