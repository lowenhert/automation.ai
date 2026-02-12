package com.lowenhert.patchmanagement.core.script.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lowenhert.patchmanagement.core.script.model.Script;

public interface ScriptRepository extends JpaRepository<Script, Long> {
    // Custom query methods can be added here
    // Example: List<Script> findByType(ScriptType type);
}