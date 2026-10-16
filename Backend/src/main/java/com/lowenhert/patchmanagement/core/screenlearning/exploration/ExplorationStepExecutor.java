package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.config.exception.ServiceUnavailableException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import com.lowenhert.patchmanagement.core.screenlearning.adb.ScreenLearningAdbService;
import com.lowenhert.patchmanagement.core.screenlearning.capture.ScreenCaptureService;
import com.lowenhert.patchmanagement.core.screenlearning.capture.ScreenStorage;
import com.lowenhert.patchmanagement.core.screenlearning.capture.dto.ScreenCaptureResponse;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Executes a single exploration step. This is the only code path that sends remote keys to a device. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExplorationStepExecutor {

    private final ScreenLearningAdbService adbService;
    private final ScreenCaptureService captureService;
    private final CapturedScreenRepository capturedScreenRepository;
    private final ScreenStorage storage;
    private final ScreenIdentifier identifier;
    private final ScreenInterpretationService interpretationService;
    private final DestructiveActionGuard guard;
    private final ScreenNodeRepository nodeRepository;
    private final ScreenTransitionRepository transitionRepository;
    private final ExplorationStepRepository stepRepository;

    public record Observation(CapturedScreen screen, ScreenNode node, boolean newScreen, boolean interpreted) {
    }

    public record StepOutcome(ExplorationStep step, Observation observation, boolean newTransition) {
        public boolean ok() {
            return step.getStatus() == ExplorationStep.Status.OK;
        }

        public boolean blocked() {
            return step.getStatus() == ExplorationStep.Status.BLOCKED;
        }
    }

    /** Captures the current screen (Phase 1 pipeline: capture → OCR → history) and identifies it. */
    public Observation observe(String deviceSerial, boolean mayInterpret) {
        ScreenCaptureResponse capture = captureService.capture(deviceSerial);
        CapturedScreen screen = capturedScreenRepository.findWithTextRegionsByCaptureId(capture.getCaptureId())
                .orElseThrow(() -> new ServiceUnavailableException("Captured screen was not stored in history"));
        ScreenIdentifier.Identification id = identifier.identify(deviceSerial, screen);
        boolean interpreted = false;
        if (id.isNew() && mayInterpret && interpretationService.isEnabled()) {
            applyInterpretation(id.node(), screen);
            interpreted = true;
        }
        return new Observation(screen, id.node(), id.isNew(), interpreted);
    }

    /** Why {@code key} must not be sent right now, or empty if it is allowed. */
    public Optional<String> blockReason(ExplorationSession session, RemoteKey key, boolean auto) {
        if (auto && key == RemoteKey.SELECT && !session.isAllowSelect()) {
            return Optional.of("SELECT is not enabled for this auto-exploration session");
        }
        if (auto && key == RemoteKey.HOME && !session.isAllowHome()) {
            return Optional.of("HOME is not enabled for this auto-exploration session");
        }
        if (key == RemoteKey.SELECT) {
            ScreenNode node = session.getCurrentNodeId() == null ? null
                    : nodeRepository.findById(session.getCurrentNodeId()).orElse(null);
            CapturedScreen screen = session.getCurrentCaptureId() == null ? null
                    : capturedScreenRepository.findByCaptureId(session.getCurrentCaptureId()).orElse(null);
            return guard.selectBlockReason(node, screen);
        }
        return Optional.empty();
    }

    /** Perceptual hash of a stored capture (used to tell focus states apart within one screen). */
    public String currentHash(String captureId) {
        return captureId == null ? null
                : capturedScreenRepository.findByCaptureId(captureId).map(CapturedScreen::getPhash).orElse(null);
    }

    public StepOutcome perform(ExplorationSession session, RemoteKey key, boolean auto, boolean mayInterpret) {
        ExplorationStep.ExplorationStepBuilder step = ExplorationStep.builder()
                .sessionId(session.getId())
                .stepIndex(nextStepIndex(session))
                .action(key)
                .fromNodeId(session.getCurrentNodeId())
                .fromCaptureId(session.getCurrentCaptureId())
                .autoTriggered(auto);

        Optional<String> blocked = blockReason(session, key, auto);
        if (blocked.isPresent()) {
            log.info("Session {}: {} blocked - {}", session.getId(), key, blocked.get());
            return new StepOutcome(stepRepository.save(step.status(ExplorationStep.Status.BLOCKED)
                    .message(truncate(blocked.get())).build()), null, false);
        }

        long started = System.nanoTime();
        try {
            adbService.sendKey(session.getDeviceSerial(), key);
            sleep(session.getSettleMs());
            Observation observation = observe(session.getDeviceSerial(), mayInterpret);
            boolean newTransition = recordTransition(session, key, observation.node());

            ExplorationStep saved = stepRepository.save(step.status(ExplorationStep.Status.OK)
                    .toNodeId(observation.node().getId())
                    .toCaptureId(observation.screen().getCaptureId())
                    .newScreen(observation.newScreen())
                    .sameScreen(Objects.equals(session.getCurrentNodeId(), observation.node().getId()))
                    .durationMs(elapsedMs(started))
                    .build());
            log.info("Session {} step {}: {} → '{}'{}", session.getId(), saved.getStepIndex(), key,
                    observation.node().getLabel(), observation.newScreen() ? " (new)" : "");
            return new StepOutcome(saved, observation, newTransition);
        } catch (ServiceUnavailableException | BadRequestException | ResourceNotFoundException e) {
            log.warn("Session {}: {} failed - {}", session.getId(), key, e.getMessage());
            return new StepOutcome(stepRepository.save(step.status(ExplorationStep.Status.FAILED)
                    .durationMs(elapsedMs(started)).message(truncate(e.getMessage())).build()), null, false);
        }
    }

    private boolean recordTransition(ExplorationSession session, RemoteKey key, ScreenNode to) {
        if (session.getCurrentNodeId() == null) return false;
        Instant now = Instant.now();
        Optional<ScreenTransition> existing = transitionRepository
                .findByFromNodeIdAndActionAndToNodeId(session.getCurrentNodeId(), key, to.getId());
        if (existing.isPresent()) {
            ScreenTransition edge = existing.get();
            edge.setObservationCount(edge.getObservationCount() + 1);
            edge.setLastSeenAt(now);
            transitionRepository.save(edge);
            return false;
        }
        transitionRepository.save(ScreenTransition.builder()
                .deviceSerial(session.getDeviceSerial())
                .fromNode(nodeRepository.getReferenceById(session.getCurrentNodeId()))
                .action(key)
                .toNode(to)
                .observationCount(1)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build());
        return true;
    }

    private void applyInterpretation(ScreenNode node, CapturedScreen screen) {
        ScreenInterpretationService.Result result = interpretationService.interpret(screen, storage.imagePath(screen.getCaptureId()));
        if (result.interpretation() == null) {
            node.setInterpretationError(truncate(result.error()));
            nodeRepository.save(node);
            return;
        }
        ScreenInterpretationService.Interpretation i = result.interpretation();
        node.setLabel(i.screenName().strip().length() > 120 ? i.screenName().strip().substring(0, 120) : i.screenName().strip());
        node.setScreenType(i.screenType());
        node.setDescription(truncate(i.description()));
        // The LLM may only make a screen more restricted, never less
        ScreenNode.RiskLevel llmRisk = ScreenNode.RiskLevel.valueOf(i.risk());
        if (llmRisk.ordinal() > node.getRiskLevel().ordinal()) node.setRiskLevel(llmRisk);
        node.setLabelSource(ScreenNode.LabelSource.LLM);
        node.setInterpretationInput(result.input());
        node.setInterpretationModel(result.model());
        node.setInterpretationError(null);
        nodeRepository.save(node);
    }

    private int nextStepIndex(ExplorationSession session) {
        return (int) stepRepository.countBySessionId(session.getId()) + 1;
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }

    private static void sleep(int ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Exploration step was interrupted");
        }
    }
}
