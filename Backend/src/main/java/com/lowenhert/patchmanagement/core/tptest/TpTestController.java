package com.lowenhert.patchmanagement.core.tptest;

import com.lowenhert.patchmanagement.core.tptest.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tp-test")
@RequiredArgsConstructor
public class TpTestController {

    private final TpTestService service;

    // ═══════════════════════════════════════════════════════════════════════
    // PROJECTS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/projects")
    public ResponseEntity<List<Map<String, Object>>> getProjects() {
        return ResponseEntity.ok(service.getAllProjects());
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<TpProject> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(service.getProject(id));
    }

    @PostMapping("/projects")
    public ResponseEntity<TpProject> createProject(@RequestBody TpProject project) {
        log.info("Creating project: {}", project.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProject(project));
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        log.info("Deleting project: id={}", id);
        service.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUITES
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/projects/{projectId}/suites")
    public ResponseEntity<List<TpSuite>> getSuites(@PathVariable Long projectId) {
        return ResponseEntity.ok(service.getSuitesByProject(projectId));
    }

    @PostMapping("/suites")
    public ResponseEntity<TpSuite> createSuite(@RequestBody TpSuite suite) {
        log.info("Creating suite: {} in project {}", suite.getName(), suite.getProjectId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSuite(suite));
    }

    @DeleteMapping("/suites/{id}")
    public ResponseEntity<Void> deleteSuite(@PathVariable Long id) {
        service.deleteSuite(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SECTIONS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/suites/{suiteId}/sections")
    public ResponseEntity<List<TpSection>> getSections(@PathVariable Long suiteId) {
        return ResponseEntity.ok(service.getSectionsBySuite(suiteId));
    }

    @PostMapping("/sections")
    public ResponseEntity<TpSection> createSection(@RequestBody TpSection section) {
        log.info("Creating section: {} in suite {}", section.getName(), section.getSuiteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSection(section));
    }

    @DeleteMapping("/sections/{id}")
    public ResponseEntity<Void> deleteSection(@PathVariable Long id) {
        service.deleteSection(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST CASES
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/suites/{suiteId}/cases")
    public ResponseEntity<List<TpTestCase>> getCasesBySuite(@PathVariable Long suiteId) {
        return ResponseEntity.ok(service.getCasesBySuite(suiteId));
    }

    @GetMapping("/sections/{sectionId}/cases")
    public ResponseEntity<List<TpTestCase>> getCasesBySection(@PathVariable Long sectionId) {
        return ResponseEntity.ok(service.getCasesBySection(sectionId));
    }

    @GetMapping("/cases/{id}")
    public ResponseEntity<TpTestCase> getCase(@PathVariable Long id) {
        return ResponseEntity.ok(service.getCase(id));
    }

    @PostMapping("/cases")
    public ResponseEntity<TpTestCase> createCase(@RequestBody TpTestCase testCase) {
        log.info("Creating test case: {} in section {}", testCase.getTitle(), testCase.getSectionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCase(testCase));
    }

    @PutMapping("/cases/{id}")
    public ResponseEntity<TpTestCase> updateCase(@PathVariable Long id, @RequestBody TpTestCase testCase) {
        return ResponseEntity.ok(service.updateCase(id, testCase));
    }

    @DeleteMapping("/cases/{id}")
    public ResponseEntity<Void> deleteCase(@PathVariable Long id) {
        service.deleteCase(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST RUNS
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/projects/{projectId}/runs")
    public ResponseEntity<List<Map<String, Object>>> getRunsByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(service.getRunsByProject(projectId));
    }

    @GetMapping("/runs/all")
    public ResponseEntity<List<Map<String, Object>>> getAllRuns() {
        return ResponseEntity.ok(service.getAllRuns());
    }

    @GetMapping("/runs/assigned/{assignedTo}")
    public ResponseEntity<List<Map<String, Object>>> getRunsByAssignedTo(@PathVariable String assignedTo) {
        return ResponseEntity.ok(service.getRunsByAssignedTo(assignedTo));
    }

    @PostMapping("/runs")
    public ResponseEntity<TpTestRun> createRun(@RequestBody TpTestRun run) {
        log.info("Creating test run: {} for project {} suite {}", run.getName(), run.getProjectId(), run.getSuiteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createRun(run));
    }

    @DeleteMapping("/runs/{id}")
    public ResponseEntity<Void> deleteRun(@PathVariable Long id) {
        service.deleteRun(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RUN CASES
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/runs/{runId}/cases")
    public ResponseEntity<List<Map<String, Object>>> getRunCases(@PathVariable Long runId) {
        return ResponseEntity.ok(service.getRunCases(runId));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESULTS
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/run-cases/{runCaseId}/results")
    public ResponseEntity<TpResult> addResult(@PathVariable Long runCaseId, @RequestBody TpResult result) {
        log.info("Adding result for runCase {}: status={}", runCaseId, result.getStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addResult(runCaseId, result));
    }

    @GetMapping("/run-cases/{runCaseId}/results")
    public ResponseEntity<List<TpResult>> getResults(@PathVariable Long runCaseId) {
        return ResponseEntity.ok(service.getResults(runCaseId));
    }
}
