package com.lowenhert.patchmanagement.core.ai.planner;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TestPlannerDtos {

    private TestPlannerDtos() {
    }

    public record GenerateRequest(String goal, String deviceId, Long startScreenId, Integer maxSteps) {
    }

    public record ExecutePlanRequest(String deviceId, Integer timeoutSeconds) {
    }

    public record ReviewRequest(AiTestPlan.Status decision, String notes) {
    }

    public record PlanSummary(Long id, AiTestPlan.Status status, String title, String goal, String deviceSerial,
                              int stepCount, int errorFlags, int warningFlags, Instant createdAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlanDetail(Long id, AiTestPlan.Status status, String title, String goal, String deviceSerial,
                             Long startScreenId, int maxSteps, int stepCount, JsonNode plan,
                             List<PlanReviewer.Flag> flags, Map<Integer, PlanReviewer.Evidence> stepEvidence,
                             JsonNode knowledge, int errorFlags, int warningFlags, String model, int attempts,
                             String createdBy, Instant createdAt, String reviewedBy, Instant reviewedAt,
                             String reviewNotes, String note, Long executionId) {
    }
}
