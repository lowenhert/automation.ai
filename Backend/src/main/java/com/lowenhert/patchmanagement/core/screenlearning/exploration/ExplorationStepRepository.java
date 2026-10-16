package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExplorationStepRepository extends JpaRepository<ExplorationStep, Long> {

    Page<ExplorationStep> findBySessionId(Long sessionId, Pageable pageable);

    long countBySessionId(Long sessionId);
}
