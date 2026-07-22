package com.lowenhert.patchmanagement.core.tptest.repository;

import com.lowenhert.patchmanagement.core.tptest.entity.TpTestRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpTestRunRepository extends JpaRepository<TpTestRun, Long> {
    List<TpTestRun> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<TpTestRun> findAllByOrderByCreatedAtDesc();
    List<TpTestRun> findByAssignedToOrderByCreatedAtDesc(String assignedTo);
    long countByProjectId(Long projectId);
    void deleteByProjectId(Long projectId);
}
