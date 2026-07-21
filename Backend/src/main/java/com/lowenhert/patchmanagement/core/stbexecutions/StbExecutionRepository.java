package com.lowenhert.patchmanagement.core.stbexecutions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StbExecutionRepository extends JpaRepository<StbExecution, Long> {
    List<StbExecution> findByStbNoOrderByCreatedAtDesc(Integer stbNo);
    List<StbExecution> findByStatusOrderByCreatedAtDesc(String status);
    List<StbExecution> findAllByOrderByCreatedAtDesc();
}
