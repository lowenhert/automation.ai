package com.lowenhert.patchmanagement.core.testlibrary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScriptExecutionRepository extends JpaRepository<ScriptExecution, Long> {

    Page<ScriptExecution> findByScriptId(Long scriptId, Pageable pageable);

    List<ScriptExecution> findByScriptIdIn(Collection<Long> scriptIds);

    long countByScriptIdIn(Collection<Long> scriptIds);

    boolean existsByScriptIdInAndStatus(Collection<Long> scriptIds, ScriptExecution.Status status);

    List<ScriptExecution> findByStatus(ScriptExecution.Status status);
}
