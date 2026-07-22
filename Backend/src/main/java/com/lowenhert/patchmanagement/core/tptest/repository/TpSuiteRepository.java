package com.lowenhert.patchmanagement.core.tptest.repository;

import com.lowenhert.patchmanagement.core.tptest.entity.TpSuite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpSuiteRepository extends JpaRepository<TpSuite, Long> {
    List<TpSuite> findByProjectIdOrderByCreatedAtAsc(Long projectId);
    void deleteByProjectId(Long projectId);
}
