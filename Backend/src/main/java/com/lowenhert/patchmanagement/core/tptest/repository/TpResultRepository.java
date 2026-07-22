package com.lowenhert.patchmanagement.core.tptest.repository;

import com.lowenhert.patchmanagement.core.tptest.entity.TpResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpResultRepository extends JpaRepository<TpResult, Long> {
    List<TpResult> findByRunCaseIdOrderByExecutedAtDesc(Long runCaseId);
    void deleteByRunCaseIdIn(List<Long> runCaseIds);
}
