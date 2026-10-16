package com.lowenhert.patchmanagement.core.testlibrary;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    Page<TestCase> findAllBy(Pageable pageable);

    Page<TestCase> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @EntityGraph(attributePaths = "events")
    Optional<TestCase> findWithEventsById(Long id);

    boolean existsByNameIgnoreCase(String name);
}
