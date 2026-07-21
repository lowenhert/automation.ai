package com.lowenhert.patchmanagement.core.stbexecutions;

import com.lowenhert.patchmanagement.core.stbexecutions.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/stb-executions")
@RequiredArgsConstructor
public class StbExecutionController {

    private final StbExecutionService service;

    // ─── Test Cases ─────────────────────────────────────────────────────────

    @PostMapping("/test-cases")
    public ResponseEntity<TestCaseResponse> createTestCase(@RequestBody CreateTestCaseRequest request) {
        log.info("Create test case: module={}, name={}", request.getModuleName(), request.getTestName());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTestCase(request));
    }

    @GetMapping("/test-cases")
    public ResponseEntity<List<TestCaseResponse>> getAllTestCases() {
        return ResponseEntity.ok(service.getAllTestCases());
    }

    @GetMapping("/test-cases/enabled")
    public ResponseEntity<List<TestCaseResponse>> getEnabledTestCases() {
        return ResponseEntity.ok(service.getEnabledTestCases());
    }

    @PatchMapping("/test-cases/{id}/toggle")
    public ResponseEntity<TestCaseResponse> toggleTestCase(@PathVariable Long id) {
        return ResponseEntity.ok(service.toggleTestCase(id));
    }

    @DeleteMapping("/test-cases/{id}")
    public ResponseEntity<Void> deleteTestCase(@PathVariable Long id) {
        service.deleteTestCase(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Executions ─────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ExecutionResponse> createExecution(@RequestBody CreateExecutionRequest request) {
        log.info("Create execution: stbNo={}, name={}", request.getStbNo(), request.getExecutionName());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createExecution(request));
    }

    @GetMapping
    public ResponseEntity<List<ExecutionResponse>> getAllExecutions() {
        return ResponseEntity.ok(service.getAllExecutions());
    }

    @GetMapping("/box/{stbNo}")
    public ResponseEntity<List<ExecutionResponse>> getExecutionsByStb(@PathVariable Integer stbNo) {
        return ResponseEntity.ok(service.getExecutionsByStb(stbNo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExecutionResponse> getExecutionDetail(@PathVariable Long id) {
        return ResponseEntity.ok(service.getExecutionDetail(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ExecutionResponse> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return ResponseEntity.ok(service.updateExecutionStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExecution(@PathVariable Long id) {
        service.deleteExecution(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Trigger Execution ──────────────────────────────────────────────────

    @PostMapping("/{id}/trigger")
    public ResponseEntity<ExecutionResponse> triggerExecution(
            @PathVariable Long id,
            @RequestBody TriggerRequest request) {
        log.info("Trigger execution: id={}, device={}, modules={}", id, request.getDeviceId(), request.getModules());
        return ResponseEntity.ok(service.triggerExecution(id, request.getDeviceId(), request.getModules()));
    }

    // ─── Execution Results ──────────────────────────────────────────────────

    @PostMapping("/results")
    public ResponseEntity<ExecutionResultResponse> addResult(@RequestBody AddResultRequest request) {
        log.info("Add result: execId={}, step={}", request.getExecutionId(), request.getStepName());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addResult(request));
    }

    @GetMapping("/{executionId}/results")
    public ResponseEntity<List<ExecutionResultResponse>> getResults(@PathVariable Long executionId) {
        return ResponseEntity.ok(service.getResultsByExecution(executionId));
    }
}
