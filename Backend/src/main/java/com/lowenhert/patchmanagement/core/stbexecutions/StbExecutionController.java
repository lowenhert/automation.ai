package com.lowenhert.patchmanagement.core.stbexecutions;

import com.lowenhert.patchmanagement.core.stbexecutions.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/stb-executions")
@RequiredArgsConstructor
public class StbExecutionController {

    private final StbExecutionService service;
    private final ScriptLogService scriptLogService;

    private static final String SCRIPTS_DIR = System.getProperty("user.dir") + "/../Long-Run-Automation-with-Parallel-Execution/test";

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

    // ─── Real-time Log Streaming (SSE) ──────────────────────────────────────

    @GetMapping(value = "/{executionId}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@PathVariable Long executionId) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        // Send existing logs first
        List<String> existingLogs = scriptLogService.getLogs(executionId);
        try {
            for (String line : existingLogs) {
                emitter.send(SseEmitter.event().data(line));
            }
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        // Subscribe to new logs
        java.util.function.Consumer<String> subscriber = line -> {
            try {
                emitter.send(SseEmitter.event().data(line));
            } catch (IOException e) {
                scriptLogService.unsubscribe(executionId, null); // will be cleaned up
            }
        };

        scriptLogService.subscribe(executionId, subscriber);

        emitter.onCompletion(() -> scriptLogService.unsubscribe(executionId, subscriber));
        emitter.onTimeout(() -> scriptLogService.unsubscribe(executionId, subscriber));
        emitter.onError(e -> scriptLogService.unsubscribe(executionId, subscriber));

        return emitter;
    }

    @GetMapping("/{executionId}/logs")
    public ResponseEntity<List<String>> getLogs(@PathVariable Long executionId) {
        return ResponseEntity.ok(scriptLogService.getLogs(executionId));
    }

    // ─── Script File Management ─────────────────────────────────────────────

    @GetMapping("/scripts")
    public ResponseEntity<List<Map<String, Object>>> listScripts() {
        try {
            Path scriptsPath = Paths.get(SCRIPTS_DIR);
            if (!Files.exists(scriptsPath)) {
                return ResponseEntity.ok(List.of());
            }
            List<Map<String, Object>> scripts = Files.list(scriptsPath)
                    .filter(p -> p.getFileName().toString().endsWith(".py"))
                    .filter(p -> p.getFileName().toString().startsWith("test_"))
                    .map(p -> {
                        try {
                            Map<String, Object> info = new java.util.LinkedHashMap<>();
                            info.put("name", p.getFileName().toString());
                            info.put("size", Files.size(p));
                            info.put("lastModified", Files.getLastModifiedTime(p).toMillis());
                            return info;
                        } catch (IOException e) {
                            return Map.<String, Object>of("name", p.getFileName().toString());
                        }
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(scripts);
        } catch (IOException e) {
            log.error("Failed to list scripts: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    @GetMapping("/scripts/{fileName}")
    public ResponseEntity<Map<String, String>> getScript(@PathVariable String fileName) {
        if (!fileName.endsWith(".py") || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Path filePath = Paths.get(SCRIPTS_DIR, fileName);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            String content = Files.readString(filePath);
            return ResponseEntity.ok(Map.of("name", fileName, "content", content));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/scripts")
    public ResponseEntity<Map<String, String>> createScript(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String content = body.get("content");
        if (name == null || content == null || !name.endsWith(".py") || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        if (!name.startsWith("test_")) {
            name = "test_" + name;
        }
        try {
            Path scriptsPath = Paths.get(SCRIPTS_DIR);
            Files.createDirectories(scriptsPath);
            Path filePath = scriptsPath.resolve(name);
            Files.writeString(filePath, content);
            log.info("Script created/updated: {}", name);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("name", name, "message", "Script saved"));
        } catch (IOException e) {
            log.error("Failed to save script: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/scripts/{fileName}")
    public ResponseEntity<Map<String, String>> updateScript(@PathVariable String fileName, @RequestBody Map<String, String> body) {
        if (!fileName.endsWith(".py") || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        String content = body.get("content");
        if (content == null) return ResponseEntity.badRequest().build();
        try {
            Path filePath = Paths.get(SCRIPTS_DIR, fileName);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            Files.writeString(filePath, content);
            log.info("Script updated: {}", fileName);
            return ResponseEntity.ok(Map.of("name", fileName, "message", "Script updated"));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/scripts/{fileName}")
    public ResponseEntity<Void> deleteScript(@PathVariable String fileName) {
        if (!fileName.endsWith(".py") || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Path filePath = Paths.get(SCRIPTS_DIR, fileName);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            Files.delete(filePath);
            log.info("Script deleted: {}", fileName);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
