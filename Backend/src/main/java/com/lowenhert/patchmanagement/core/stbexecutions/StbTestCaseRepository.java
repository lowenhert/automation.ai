package com.lowenhert.patchmanagement.core.stbexecutions;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StbTestCaseRepository extends JpaRepository<StbTestCase, Long> {
    List<StbTestCase> findByEnabledTrue();
    List<StbTestCase> findByCategory(String category);
    List<StbTestCase> findByModuleName(String moduleName);
}
