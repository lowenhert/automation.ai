package com.lowenhert.patchmanagement.core.agentic;

import com.lowenhert.patchmanagement.core.testlibrary.TestScript;
import java.time.Instant;

public final class AgenticDtos {

    private AgenticDtos() {
    }

    public record AgenticRunRequest(
            String deviceId,
            String goal,
            Integer maxSteps,
            Integer timeoutSeconds,
            TestScript.Language language
    ) {
    }

    public record AgenticReExecuteRequest(
            Long scriptId,
            String deviceId,
            String content,
            Integer timeoutSeconds
    ) {
    }

    public record AgenticRunResult(
            Long planId,
            Long testCaseId,
            Long scriptId,
            Long executionId,
            String status
    ) {
    }

    public record AgenticMetrics(
            Double cpuPercent,
            Double memMb,
            Instant timestamp
    ) {
    }
}
