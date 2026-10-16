package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;

import java.time.Instant;
import java.util.List;

/** Request/response types for the exploration API. */
public final class ExplorationDtos {

    private ExplorationDtos() {
    }

    public record StartRequest(String deviceId, ExplorationSession.Mode mode, Integer maxSteps,
                               Integer maxDurationSeconds, Integer settleMs, Boolean allowSelect, Boolean allowHome) {
    }

    public record ActionRequest(RemoteKey action) {
    }

    public record NodeRef(Long id, String label) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionDto(Long id, String deviceSerial, ExplorationSession.Mode mode, ExplorationSession.Status status,
                             int maxSteps, int maxDurationSeconds, int settleMs, boolean allowSelect, boolean allowHome,
                             int stepsTaken, int newScreens, NodeRef currentNode, String currentCaptureId,
                             boolean stopRequested, String stopReason, Instant startedAt, Instant endedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepDto(Long id, int stepIndex, RemoteKey action, ExplorationStep.Status status,
                          NodeRef from, NodeRef to, String fromCaptureId, String toCaptureId,
                          boolean newScreen, boolean sameScreen, boolean autoTriggered, Long durationMs,
                          String message, Instant createdAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NodeDto(Long id, String label, String screenType, String description, ScreenNode.RiskLevel riskLevel,
                          ScreenNode.LabelSource labelSource, ScreenNode.InterpretationInput interpretationInput,
                          String interpretationModel, String interpretationError, int visitCount,
                          String representativeCaptureId, String imageUrl, Instant firstSeenAt, Instant lastSeenAt) {
    }

    public record EdgeDto(Long id, Long from, Long to, RemoteKey action, int count, Instant lastSeenAt) {
    }

    public record GraphDto(String deviceSerial, List<NodeDto> nodes, List<EdgeDto> edges) {
    }
}
