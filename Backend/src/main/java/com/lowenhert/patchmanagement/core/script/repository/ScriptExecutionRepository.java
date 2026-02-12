package com.lowenhert.patchmanagement.core.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lowenhert.patchmanagement.core.script.model.ScriptExecution;

public interface ScriptExecutionRepository extends JpaRepository<ScriptExecution, Long> {
    // Custom query methods can be added here
    // Example: List<ScriptExecution> findByScriptId(Long scriptId);
}