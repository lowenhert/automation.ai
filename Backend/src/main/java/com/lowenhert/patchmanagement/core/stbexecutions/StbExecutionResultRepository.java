package com.lowenhert.patchmanagement.core.stbexecutions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StbExecutionResultRepository extends JpaRepository<StbExecutionResult, Long> {
    List<StbExecutionResult> findByExecutionIdOrderByStepNumberAsc(Long executionId);
    List<StbExecutionResult> findByExecutionIdAndModuleName(Long executionId, String moduleName);
}
