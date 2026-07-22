package com.lowenhert.patchmanagement.core.tptest.repository;

import com.lowenhert.patchmanagement.core.tptest.entity.TpTestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpTestCaseRepository extends JpaRepository<TpTestCase, Long> {
    List<TpTestCase> findBySectionIdOrderByCreatedAtAsc(Long sectionId);
    List<TpTestCase> findBySectionIdIn(List<Long> sectionIds);
    long countBySectionIdIn(List<Long> sectionIds);
    void deleteBySectionId(Long sectionId);
    void deleteBySectionIdIn(List<Long> sectionIds);
}
