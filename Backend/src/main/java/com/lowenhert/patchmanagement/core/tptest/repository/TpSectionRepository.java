package com.lowenhert.patchmanagement.core.tptest.repository;

import com.lowenhert.patchmanagement.core.tptest.entity.TpSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpSectionRepository extends JpaRepository<TpSection, Long> {
    List<TpSection> findBySuiteIdOrderByCreatedAtAsc(Long suiteId);
    void deleteBySuiteId(Long suiteId);
}
