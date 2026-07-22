package com.lowenhert.patchmanagement.core.tptest.repository;

import com.lowenhert.patchmanagement.core.tptest.entity.TpRunCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpRunCaseRepository extends JpaRepository<TpRunCase, Long> {
    List<TpRunCase> findByRunId(Long runId);
    long countByRunId(Long runId);
    long countByRunIdAndLatestStatus(Long runId, String status);
    void deleteByRunId(Long runId);
}
