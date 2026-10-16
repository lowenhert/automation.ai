package com.lowenhert.patchmanagement.core.testlibrary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestScriptRepository extends JpaRepository<TestScript, Long> {

    Page<TestScript> findAllBy(Pageable pageable);

    Page<TestScript> findByTestCaseId(Long testCaseId, Pageable pageable);

    List<TestScript> findByTestCaseId(Long testCaseId);

    long countByTestCaseId(Long testCaseId);
}
