package com.lowenhert.patchmanagement.core.testlibrary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

public final class TestLibraryDtos {

    private TestLibraryDtos() {
    }

    // ── Test cases ──────────────────────────────────────────────────────────

    public record TestCaseRequest(String name, String description, String deviceSerial) {
    }

    public record RenameRequest(String name) {
    }

    public record EventRequest(TestCaseEvent.Action action, String target, String expectedState, Integer timeoutMs) {
    }

    public record EventDto(Long id, int position, TestCaseEvent.Action action, String target, String expectedState,
                           int timeoutMs) {
        static EventDto of(TestCaseEvent e) {
            return new EventDto(e.getId(), e.getPosition(), e.getAction(), e.getTarget(), e.getExpectedState(), e.getTimeoutMs());
        }
    }

    public record TestCaseSummary(Long id, String name, String description, Long sourcePlanId, String deviceSerial,
                                  int eventCount, long scriptCount, String createdBy, Instant createdAt, Instant updatedAt) {
    }

    public record TestCaseDetail(Long id, String name, String description, Long sourcePlanId, String deviceSerial,
                                 String createdBy, Instant createdAt, Instant updatedAt, List<EventDto> events,
                                 List<ScriptSummary> scripts) {
    }

    public record DeleteImpact(String type, Long id, String name, int events, long scripts, long executions,
                               boolean running) {
    }

    public record FromPlanRequest(Long planId, String name) {
    }

    public record GenerateRequest(TestScript.Language language, String name) {
    }

    // ── Scripts ─────────────────────────────────────────────────────────────

    public record ScriptRequest(Long testCaseId, String name, TestScript.Language language, String content) {
    }

    public record ScriptUpdateRequest(String name, String content, Long expectedVersion) {
    }

    public record ApproveRequest(String contentHash) {
    }

    public record ScriptSummary(Long id, Long testCaseId, String name, TestScript.Language language,
                                TestScript.Origin origin, TestScript.Status status, boolean executable,
                                Boolean validationValid, String contentHash, Instant updatedAt) {
        static ScriptSummary of(TestScript s, Boolean validationValid) {
            return new ScriptSummary(s.getId(), s.getTestCaseId(), s.getName(), s.getLanguage(), s.getOrigin(),
                    s.getStatus(), s.isExecutable(), validationValid, s.getContentHash(), s.getUpdatedAt());
        }
    }

    public record ScriptDetail(Long id, Long testCaseId, String name, TestScript.Language language,
                               TestScript.Origin origin, TestScript.Status status, boolean executable, String content,
                               String contentHash, Long version, Validation validation, boolean validationCurrent,
                               String approvedHash, String approvedBy, Instant approvedAt, String createdBy,
                               Instant createdAt, Instant updatedAt) {
    }

    /** Output of validate_script.py. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Validation(boolean valid, List<Issue> errors, List<Issue> warnings, List<String> tests,
                             List<ValidationAction> actions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(int line, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValidationAction(int line, String action, String detail) {
    }

    public record Preview(Long scriptId, String name, TestScript.Language language, String content, String contentHash,
                          Validation validation, boolean executable) {
    }

    // ── Executions ──────────────────────────────────────────────────────────

    /**
     * {@code contentHash} must equal the reviewed content hash and {@code confirm} must be true: the user sees and
     * approves exactly what runs.
     */
    public record ExecuteRequest(Long scriptId, String deviceId, String contentHash, Integer timeoutSeconds,
                                 Boolean confirm) {
    }

    public record TestResult(String name, String status, Double durationSeconds, String message) {
    }

    public record ExecutionDto(Long id, Long scriptId, String deviceSerial, TestScript.Language language,
                               String contentHash, ScriptExecution.Status status, int timeoutSeconds, Integer exitCode,
                               Integer testsTotal, Integer testsPassed, Integer testsFailed, Integer testsSkipped,
                               String errorMessage, List<TestResult> results, List<String> screenshots,
                               String startedBy, Instant startedAt, Instant endedAt) {
    }

    public record LogChunk(Long executionId, long offset, long nextOffset, String text, boolean complete,
                           ScriptExecution.Status status) {
    }
}
