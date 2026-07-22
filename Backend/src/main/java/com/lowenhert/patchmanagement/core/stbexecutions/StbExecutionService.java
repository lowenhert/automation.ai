package com.lowenhert.patchmanagement.core.stbexecutions;

import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.stbexecutions.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StbExecutionService {

    private final StbTestCaseRepository testCaseRepo;
    private final StbExecutionRepository executionRepo;
    private final StbExecutionResultRepository resultRepo;
    private final ScriptLogService scriptLogService;

    // ─── Test Cases ─────────────────────────────────────────────────────────

    public TestCaseResponse createTestCase(CreateTestCaseRequest req) {
        StbTestCase tc = StbTestCase.builder()
                .moduleName(req.getModuleName())
                .testName(req.getTestName())
                .description(req.getDescription())
                .category(req.getCategory())
                .totalSteps(req.getTotalSteps())
                .build();
        testCaseRepo.save(tc);
        log.info("TestCase created: id={}, module={}", tc.getId(), tc.getModuleName());
        return mapTestCase(tc);
    }

    public List<TestCaseResponse> getAllTestCases() {
        return testCaseRepo.findAll().stream().map(this::mapTestCase).collect(Collectors.toList());
    }

    public List<TestCaseResponse> getEnabledTestCases() {
        return testCaseRepo.findByEnabledTrue().stream().map(this::mapTestCase).collect(Collectors.toList());
    }

    public void deleteTestCase(Long id) {
        testCaseRepo.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("TestCase not found: " + id));
        testCaseRepo.deleteById(id);
        log.info("TestCase deleted: id={}", id);
    }

    public TestCaseResponse toggleTestCase(Long id) {
        StbTestCase tc = testCaseRepo.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("TestCase not found: " + id));
        tc.setEnabled(!tc.getEnabled());
        testCaseRepo.save(tc);
        log.info("TestCase toggled: id={}, enabled={}", id, tc.getEnabled());
        return mapTestCase(tc);
    }

    // ─── Executions ─────────────────────────────────────────────────────────

    public ExecutionResponse createExecution(CreateExecutionRequest req) {
        String testCaseIdStr = req.getTestCaseIds() != null
                ? req.getTestCaseIds().stream().map(String::valueOf).collect(Collectors.joining(","))
                : "";

        StbExecution exec = StbExecution.builder()
                .executionName(req.getExecutionName())
                .stbNo(req.getStbNo())
                .boxName(req.getBoxName())
                .testCaseIds(testCaseIdStr)
                .totalTests(req.getTestCaseIds() != null ? req.getTestCaseIds().size() : 0)
                .triggeredBy(req.getTriggeredBy())
                .notes(req.getNotes())
                .status("PENDING")
                .startTime(LocalDateTime.now())
                .build();
        executionRepo.save(exec);
        log.info("Execution created: id={}, stbNo={}, name={}", exec.getId(), exec.getStbNo(), exec.getExecutionName());
        return mapExecution(exec, false);
    }

    public List<ExecutionResponse> getAllExecutions() {
        return executionRepo.findAllByOrderByCreatedAtDesc()
                .stream().map(e -> mapExecution(e, false)).collect(Collectors.toList());
    }

    public List<ExecutionResponse> getExecutionsByStb(Integer stbNo) {
        return executionRepo.findByStbNoOrderByCreatedAtDesc(stbNo)
                .stream().map(e -> mapExecution(e, false)).collect(Collectors.toList());
    }

    public ExecutionResponse getExecutionDetail(Long id) {
        StbExecution exec = executionRepo.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Execution not found: " + id));
        return mapExecution(exec, true);
    }

    public ExecutionResponse updateExecutionStatus(Long id, String status) {
        StbExecution exec = executionRepo.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Execution not found: " + id));
        exec.setStatus(status);
        if ("RUNNING".equals(status) && exec.getStartTime() == null) {
            exec.setStartTime(LocalDateTime.now());
        }
        if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
            exec.setEndTime(LocalDateTime.now());
            if (exec.getStartTime() != null) {
                exec.setDurationSeconds(ChronoUnit.SECONDS.between(exec.getStartTime(), exec.getEndTime()));
            }
            // Recount results
            List<StbExecutionResult> results = resultRepo.findByExecutionIdOrderByStepNumberAsc(id);
            exec.setTotalTests(results.size());
            exec.setPassed((int) results.stream().filter(r -> "PASSED".equals(r.getStatus())).count());
            exec.setFailed((int) results.stream().filter(r -> "FAILED".equals(r.getStatus())).count());
            exec.setSkipped((int) results.stream().filter(r -> "SKIPPED".equals(r.getStatus())).count());
        }
        executionRepo.save(exec);
        log.info("Execution status updated: id={}, status={}", id, status);
        return mapExecution(exec, false);
    }

    public void deleteExecution(Long id) {
        executionRepo.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Execution not found: " + id));
        resultRepo.findByExecutionIdOrderByStepNumberAsc(id).forEach(r -> resultRepo.delete(r));
        executionRepo.deleteById(id);
        log.info("Execution deleted: id={}", id);
    }

    // ─── Trigger Execution ──────────────────────────────────────────────────

    private static final String PYTHON_PROJECT_PATH = System.getProperty("user.dir") + "/../Long-Run-Automation-with-Parallel-Execution";

    public ExecutionResponse triggerExecution(Long id, String deviceId, List<String> modules) {
        StbExecution exec = executionRepo.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Execution not found: " + id));

        exec.setStatus("RUNNING");
        exec.setStartTime(LocalDateTime.now());
        executionRepo.save(exec);

        String modulesStr = String.join(",", modules);
        String apiUrl = "http://localhost:8080/api";

        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "python", "dashboard_trigger.py",
                        "--execution-id", id.toString(),
                        "--device-id", deviceId,
                        "--modules", modulesStr,
                        "--api-url", apiUrl
                );
                pb.directory(new File(PYTHON_PROJECT_PATH));
                pb.redirectErrorStream(true);

                log.info("Triggering Python test runner: execId={}, device={}, modules={}", id, deviceId, modulesStr);
                scriptLogService.initExecution(id);
                scriptLogService.appendLog(id, "[SYSTEM] Starting execution: " + modulesStr);
                Process process = pb.start();

                // Capture and stream output in real-time
                new Thread(() -> {
                    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            log.info("[PythonRunner:{}] {}", id, line);
                            scriptLogService.appendLog(id, line);
                        }
                    } catch (Exception e) {
                        log.warn("Error reading Python runner output: {}", e.getMessage());
                    }
                }, "log-reader-" + id).start();

                int exitCode = process.waitFor();
                log.info("Python runner finished: execId={}, exitCode={}", id, exitCode);
                scriptLogService.appendLog(id, "[SYSTEM] Process finished with exit code: " + exitCode);

                // If the script didn't update status, do it here
                StbExecution finalExec = executionRepo.findById(id).orElse(null);
                if (finalExec != null && "RUNNING".equals(finalExec.getStatus())) {
                    finalExec.setStatus(exitCode == 0 ? "COMPLETED" : "FAILED");
                    finalExec.setEndTime(LocalDateTime.now());
                    if (finalExec.getStartTime() != null) {
                        finalExec.setDurationSeconds(ChronoUnit.SECONDS.between(finalExec.getStartTime(), finalExec.getEndTime()));
                    }
                    executionRepo.save(finalExec);
                }
                scriptLogService.cleanup(id);

            } catch (Exception e) {
                log.error("Failed to trigger execution {}: {}", id, e.getMessage());
                scriptLogService.appendLog(id, "[ERROR] " + e.getMessage());
                StbExecution failedExec = executionRepo.findById(id).orElse(null);
                if (failedExec != null) {
                    failedExec.setStatus("FAILED");
                    failedExec.setEndTime(LocalDateTime.now());
                    if (failedExec.getStartTime() != null) {
                        failedExec.setDurationSeconds(ChronoUnit.SECONDS.between(failedExec.getStartTime(), failedExec.getEndTime()));
                    }
                    failedExec.setNotes((failedExec.getNotes() != null ? failedExec.getNotes() + "\n" : "") + "Trigger error: " + e.getMessage());
                    executionRepo.save(failedExec);
                }
            }
        }, "test-runner-exec-" + id).start();

        return mapExecution(exec, false);
    }

    // ─── Execution Results ──────────────────────────────────────────────────

    public ExecutionResultResponse addResult(AddResultRequest req) {
        StbExecutionResult result = StbExecutionResult.builder()
                .executionId(req.getExecutionId())
                .testCaseId(req.getTestCaseId())
                .moduleName(req.getModuleName())
                .stepNumber(req.getStepNumber())
                .stepName(req.getStepName())
                .status(req.getStatus())
                .errorMessage(req.getErrorMessage())
                .durationMs(req.getDurationMs())
                .screenshotPath(req.getScreenshotPath())
                .build();
        resultRepo.save(result);

        // Update execution counts
        StbExecution exec = executionRepo.findById(req.getExecutionId()).orElse(null);
        if (exec != null) {
            List<StbExecutionResult> allResults = resultRepo.findByExecutionIdOrderByStepNumberAsc(req.getExecutionId());
            exec.setTotalTests(allResults.size());
            exec.setPassed((int) allResults.stream().filter(r -> "PASSED".equals(r.getStatus())).count());
            exec.setFailed((int) allResults.stream().filter(r -> "FAILED".equals(r.getStatus())).count());
            exec.setSkipped((int) allResults.stream().filter(r -> "SKIPPED".equals(r.getStatus())).count());
            executionRepo.save(exec);
        }

        log.info("Result added: execId={}, step={}, status={}", req.getExecutionId(), req.getStepName(), req.getStatus());
        return mapResult(result);
    }

    public List<ExecutionResultResponse> getResultsByExecution(Long executionId) {
        return resultRepo.findByExecutionIdOrderByStepNumberAsc(executionId)
                .stream().map(this::mapResult).collect(Collectors.toList());
    }

    // ─── Mappers ────────────────────────────────────────────────────────────

    private TestCaseResponse mapTestCase(StbTestCase tc) {
        return TestCaseResponse.builder()
                .id(tc.getId())
                .moduleName(tc.getModuleName())
                .testName(tc.getTestName())
                .description(tc.getDescription())
                .category(tc.getCategory())
                .totalSteps(tc.getTotalSteps())
                .enabled(tc.getEnabled())
                .createdAt(tc.getCreatedAt())
                .build();
    }

    private ExecutionResponse mapExecution(StbExecution exec, boolean includeResults) {
        ExecutionResponse.ExecutionResponseBuilder builder = ExecutionResponse.builder()
                .id(exec.getId())
                .executionName(exec.getExecutionName())
                .stbNo(exec.getStbNo())
                .boxName(exec.getBoxName())
                .status(exec.getStatus())
                .totalTests(exec.getTotalTests())
                .passed(exec.getPassed())
                .failed(exec.getFailed())
                .skipped(exec.getSkipped())
                .triggeredBy(exec.getTriggeredBy())
                .startTime(exec.getStartTime())
                .endTime(exec.getEndTime())
                .durationSeconds(exec.getDurationSeconds())
                .notes(exec.getNotes())
                .createdAt(exec.getCreatedAt());

        if (includeResults) {
            builder.results(resultRepo.findByExecutionIdOrderByStepNumberAsc(exec.getId())
                    .stream().map(this::mapResult).collect(Collectors.toList()));
        }
        return builder.build();
    }

    private ExecutionResultResponse mapResult(StbExecutionResult r) {
        return ExecutionResultResponse.builder()
                .id(r.getId())
                .executionId(r.getExecutionId())
                .testCaseId(r.getTestCaseId())
                .moduleName(r.getModuleName())
                .stepNumber(r.getStepNumber())
                .stepName(r.getStepName())
                .status(r.getStatus())
                .errorMessage(r.getErrorMessage())
                .durationMs(r.getDurationMs())
                .screenshotPath(r.getScreenshotPath())
                .timestamp(r.getTimestamp())
                .build();
    }
}
