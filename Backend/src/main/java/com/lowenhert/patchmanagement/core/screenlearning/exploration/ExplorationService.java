package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ConflictException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbInputValidator;
import com.lowenhert.patchmanagement.core.screenlearning.adb.DeviceReservations;
import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import com.lowenhert.patchmanagement.core.screenlearning.adb.ScreenLearningAdbService;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ExplorationDtos.*;
import com.lowenhert.patchmanagement.core.screenlearning.history.dto.PageResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Bounded screen exploration. One open session per device; every session has hard step and time limits;
 * only allow-listed remote keys are sent, and SELECT is guarded against destructive screens.
 */
@Slf4j
@Service
public class ExplorationService {

    /** Auto-exploration order: move focus first, then select, then go back. HOME is opt-in and used last. */
    static final List<RemoteKey> AUTO_ORDER = List.of(RemoteKey.DPAD_DOWN, RemoteKey.DPAD_RIGHT, RemoteKey.SELECT,
            RemoteKey.DPAD_UP, RemoteKey.DPAD_LEFT, RemoteKey.BACK, RemoteKey.HOME);
    static final int MAX_SETTLE_MS = 10_000;
    static final String RESERVATION_OWNER = "exploration session";
    static final int MAX_PAGE_SIZE = 100;

    private final ExplorationSessionRepository sessionRepository;
    private final ExplorationStepRepository stepRepository;
    private final ScreenNodeRepository nodeRepository;
    private final ScreenTransitionRepository transitionRepository;
    private final ExplorationStepExecutor executor;
    private final ScreenLearningAdbService adbService;
    private final ExplorationProperties limits;
    private final DeviceReservations reservations;

    private final ConcurrentMap<String, Long> activeByDevice = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();
    private final ExecutorService autoRunner = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "exploration-auto");
        t.setDaemon(true);
        return t;
    });

    public ExplorationService(ExplorationSessionRepository sessionRepository, ExplorationStepRepository stepRepository,
                              ScreenNodeRepository nodeRepository, ScreenTransitionRepository transitionRepository,
                              ExplorationStepExecutor executor, ScreenLearningAdbService adbService,
                              ExplorationProperties limits, DeviceReservations reservations) {
        this.sessionRepository = sessionRepository;
        this.stepRepository = stepRepository;
        this.nodeRepository = nodeRepository;
        this.transitionRepository = transitionRepository;
        this.executor = executor;
        this.adbService = adbService;
        this.limits = limits;
        this.reservations = reservations;
    }

    // ── Session lifecycle ───────────────────────────────────────────────────

    public SessionDto start(StartRequest request, String username) {
        if (request == null || request.mode() == null) throw new BadRequestException("mode is required (MANUAL or AUTO)");
        int maxSteps = bounded("maxSteps", request.maxSteps(), limits.getDefaultMaxSteps(), 1, limits.getMaxStepsLimit());
        int maxDuration = bounded("maxDurationSeconds", request.maxDurationSeconds(), limits.getDefaultMaxDurationSeconds(),
                10, limits.getMaxDurationLimitSeconds());
        int settleMs = bounded("settleMs", request.settleMs(), limits.getDefaultSettleMs(), limits.getMinSettleMs(), MAX_SETTLE_MS);
        String serial = adbService.requireOnlineDevice(request.deviceId()).getSerial();

        releaseIfExpired(serial);
        if (activeByDevice.putIfAbsent(serial, -1L) != null) {
            throw new ConflictException("An exploration session is already open for " + serial + "; stop it first");
        }
        if (!reservations.tryReserve(serial, RESERVATION_OWNER)) {
            activeByDevice.remove(serial);
            throw new ConflictException("Device " + serial + " is busy (" + reservations.owner(serial).orElse("in use") + ")");
        }
        ExplorationSession session = null;
        try {
            session = sessionRepository.save(ExplorationSession.builder()
                    .deviceSerial(serial)
                    .mode(request.mode())
                    .status(request.mode() == ExplorationSession.Mode.AUTO ? ExplorationSession.Status.RUNNING
                            : ExplorationSession.Status.ACTIVE)
                    .maxSteps(maxSteps)
                    .maxDurationSeconds(maxDuration)
                    .settleMs(settleMs)
                    .allowSelect(Boolean.TRUE.equals(request.allowSelect()))
                    .allowHome(Boolean.TRUE.equals(request.allowHome()))
                    .startedBy(username)
                    .build());
            activeByDevice.put(serial, session.getId());

            ExplorationStepExecutor.Observation start = executor.observe(serial, true);
            session.setStartNodeId(start.node().getId());
            session.setCurrentNodeId(start.node().getId());
            session.setCurrentCaptureId(start.screen().getCaptureId());
            session.setNewScreens(start.newScreen() ? 1 : 0);
            session = sessionRepository.save(session);
            log.info("Exploration session {} started on {} ({}, maxSteps={}, maxDuration={}s)",
                    session.getId(), serial, session.getMode(), maxSteps, maxDuration);

            if (session.getMode() == ExplorationSession.Mode.AUTO) {
                stopFlags.put(session.getId(), new AtomicBoolean(false));
                Long id = session.getId();
                autoRunner.submit(() -> runAuto(id, start.interpreted() ? 1 : 0));
            }
            return toDto(session);
        } catch (RuntimeException e) {
            if (session != null && session.getId() != null) {
                finish(session, ExplorationSession.Status.FAILED, "Could not capture the start screen: " + e.getMessage());
            } else {
                activeByDevice.remove(serial);
                reservations.release(serial, RESERVATION_OWNER);
            }
            throw e;
        }
    }

    /** A MANUAL session past its limits would otherwise hold the device until someone sends another action. */
    private void releaseIfExpired(String serial) {
        Long holder = activeByDevice.get(serial);
        if (holder == null || holder < 0) return;
        sessionRepository.findById(holder).ifPresent(s -> {
            String limit = limitReached(s);
            if (s.getStatus().isOpen() && s.getMode() == ExplorationSession.Mode.MANUAL && limit != null) {
                finish(s, ExplorationSession.Status.LIMIT_REACHED, limit);
            }
        });
    }

    public StepDto act(Long sessionId, RemoteKey key) {
        if (key == null) throw new BadRequestException("action is required");
        ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        if (!lock.tryLock()) throw new ConflictException("Another action is still in progress for this session");
        try {
            ExplorationSession session = load(sessionId);
            if (session.getMode() != ExplorationSession.Mode.MANUAL) {
                throw new ConflictException("Actions can only be sent to MANUAL sessions");
            }
            if (!session.getStatus().isOpen()) throw new ConflictException("Session is " + session.getStatus());
            String limit = limitReached(session);
            if (limit != null) {
                finish(session, ExplorationSession.Status.LIMIT_REACHED, limit);
                throw new ConflictException(limit);
            }

            int interpretationsLeft = limits.getMaxInterpretationsPerSession() - interpretationsUpperBound(session);
            ExplorationStepExecutor.StepOutcome outcome = executor.perform(session, key, false, interpretationsLeft > 0);
            applyOutcome(session, outcome);
            session = sessionRepository.save(session);

            String after = limitReached(session);
            if (after != null) finish(session, ExplorationSession.Status.LIMIT_REACHED, after);
            return toDto(outcome.step());
        } finally {
            lock.unlock();
        }
    }

    public SessionDto stop(Long sessionId) {
        ExplorationSession session = load(sessionId);
        if (!session.getStatus().isOpen()) return toDto(session);
        if (session.getMode() == ExplorationSession.Mode.AUTO) {
            AtomicBoolean flag = stopFlags.get(sessionId);
            if (flag != null) {
                flag.set(true);
                log.info("Stop requested for auto session {}", sessionId);
                return toDto(session);
            }
            // No live runner (e.g. after restart): close it directly
            finish(session, ExplorationSession.Status.STOPPED, "Stopped by user");
            return toDto(load(sessionId));
        }
        ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        lock.lock();
        try {
            finish(load(sessionId), ExplorationSession.Status.STOPPED, "Stopped by user");
        } finally {
            lock.unlock();
        }
        return toDto(load(sessionId));
    }

    // ── Bounded auto exploration ────────────────────────────────────────────

    void runAuto(Long sessionId, int interpretationsUsed) {
        ExplorationSession session = load(sessionId);
        AtomicBoolean stop = stopFlags.getOrDefault(sessionId, new AtomicBoolean(false));
        // Frontier per visual state (node + exact hash), so focus positions within one screen are explored separately
        Map<String, Set<RemoteKey>> tried = new HashMap<>();
        int noProgress = 0;
        int failures = 0;
        int recoveries = 0;
        int interpretations = interpretationsUsed;

        try {
            while (true) {
                if (stop.get()) {
                    finish(session, ExplorationSession.Status.STOPPED, "Stopped by user");
                    return;
                }
                String limit = limitReached(session);
                if (limit != null) {
                    finish(session, ExplorationSession.Status.LIMIT_REACHED, limit);
                    return;
                }

                String state = stateKey(session);
                Set<RemoteKey> done = tried.computeIfAbsent(state, k -> EnumSet.noneOf(RemoteKey.class));
                RemoteKey next = null;
                for (RemoteKey candidate : AUTO_ORDER) {
                    if (done.contains(candidate)) continue;
                    if (executor.blockReason(session, candidate, true).isPresent()) {
                        done.add(candidate);
                        continue;
                    }
                    next = candidate;
                    break;
                }
                if (next == null) {
                    if (recoveries >= limits.getMaxRecoveryBacks()) {
                        finish(session, ExplorationSession.Status.COMPLETED, "No unexplored actions remain within reach");
                        return;
                    }
                    next = RemoteKey.BACK;
                    recoveries++;
                } else {
                    recoveries = 0;
                }
                done.add(next);

                boolean mayInterpret = interpretations < limits.getMaxInterpretationsPerSession();
                ExplorationStepExecutor.StepOutcome outcome = executor.perform(session, next, true, mayInterpret);
                if (outcome.observation() != null && outcome.observation().interpreted()) interpretations++;
                applyOutcome(session, outcome);
                session = sessionRepository.save(session);

                if (outcome.ok()) {
                    failures = 0;
                    boolean progress = outcome.observation().newScreen() || outcome.newTransition()
                            || !tried.containsKey(stateKey(session));
                    noProgress = progress ? 0 : noProgress + 1;
                    if (noProgress >= limits.getStallLimit()) {
                        finish(session, ExplorationSession.Status.STALLED,
                                "No new screens or transitions in " + noProgress + " consecutive steps");
                        return;
                    }
                } else if (!outcome.blocked() && ++failures >= limits.getMaxConsecutiveFailures()) {
                    finish(session, ExplorationSession.Status.FAILED,
                            failures + " consecutive failed steps: " + outcome.step().getMessage());
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.error("Auto exploration {} crashed", sessionId, e);
            finish(load(sessionId), ExplorationSession.Status.FAILED, "Unexpected error: " + e.getMessage());
        }
    }

    private String stateKey(ExplorationSession session) {
        String hash = executor.currentHash(session.getCurrentCaptureId());
        return session.getCurrentNodeId() + ":" + (hash != null ? hash : session.getCurrentCaptureId());
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    private void applyOutcome(ExplorationSession session, ExplorationStepExecutor.StepOutcome outcome) {
        if (outcome.blocked()) return;
        session.setStepsTaken(session.getStepsTaken() + 1);
        if (outcome.ok()) {
            session.setCurrentNodeId(outcome.observation().node().getId());
            session.setCurrentCaptureId(outcome.observation().screen().getCaptureId());
            if (outcome.observation().newScreen()) session.setNewScreens(session.getNewScreens() + 1);
        }
    }

    String limitReached(ExplorationSession session) {
        if (session.getStepsTaken() >= session.getMaxSteps()) {
            return "Step limit reached (" + session.getMaxSteps() + ")";
        }
        if (Duration.between(session.getStartedAt(), Instant.now()).getSeconds() >= session.getMaxDurationSeconds()) {
            return "Time limit reached (" + session.getMaxDurationSeconds() + "s)";
        }
        return null;
    }

    private void finish(ExplorationSession session, ExplorationSession.Status status, String reason) {
        session.setStatus(status);
        session.setStopReason(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
        session.setEndedAt(Instant.now());
        sessionRepository.save(session);
        activeByDevice.remove(session.getDeviceSerial(), session.getId());
        reservations.release(session.getDeviceSerial(), RESERVATION_OWNER);
        stopFlags.remove(session.getId());
        sessionLocks.remove(session.getId());
        log.info("Exploration session {} ended: {} - {}", session.getId(), status, reason);
    }

    /** Interpretations only happen for new screens, so the new-screen count bounds them from above. */
    private static int interpretationsUpperBound(ExplorationSession session) {
        return session.getNewScreens();
    }

    private ExplorationSession load(Long id) {
        if (id == null || id < 1) throw new BadRequestException("Invalid session id");
        return sessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exploration session not found: " + id));
    }

    static int bounded(String name, Integer requested, int defaultValue, int min, int max) {
        int value = requested == null ? defaultValue : requested;
        if (value < min || value > max) throw new BadRequestException(name + " must be between " + min + " and " + max);
        return value;
    }

    /** Sessions left open by a previous backend process cannot be resumed. */
    @EventListener(ApplicationReadyEvent.class)
    public void closeInterruptedSessions() {
        List<ExplorationSession> open = sessionRepository.findByStatusIn(
                List.of(ExplorationSession.Status.ACTIVE, ExplorationSession.Status.RUNNING));
        for (ExplorationSession session : open) {
            if (activeByDevice.containsValue(session.getId())) continue;
            session.setStatus(ExplorationSession.Status.INTERRUPTED);
            session.setStopReason("Backend restarted while the session was open");
            session.setEndedAt(Instant.now());
            sessionRepository.save(session);
        }
        if (!open.isEmpty()) log.warn("Marked {} open exploration session(s) as INTERRUPTED", open.size());
    }

    @PreDestroy
    void shutdown() {
        stopFlags.values().forEach(f -> f.set(true));
        autoRunner.shutdownNow();
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    public SessionDto get(Long id) {
        return toDto(load(id));
    }

    public PageResponse<SessionDto> list(int page, int size) {
        return PageResponse.of(sessionRepository.findAllBy(pageable(page, size, Sort.by(Sort.Direction.DESC, "id"))), this::toDto);
    }

    public PageResponse<StepDto> steps(Long sessionId, int page, int size) {
        load(sessionId);
        return PageResponse.of(stepRepository.findBySessionId(sessionId,
                pageable(page, size, Sort.by(Sort.Direction.DESC, "stepIndex"))), this::toDto);
    }

    public GraphDto graph(String deviceId) {
        String serial = AdbInputValidator.requireSerial(deviceId);
        List<NodeDto> nodes = nodeRepository.findByDeviceSerial(serial).stream()
                .sorted(Comparator.comparing(ScreenNode::getFirstSeenAt).thenComparing(ScreenNode::getId))
                .map(this::toDto).toList();
        List<EdgeDto> edges = transitionRepository.findGraphEdges(serial).stream()
                .map(t -> new EdgeDto(t.getId(), t.getFromNode().getId(), t.getToNode().getId(), t.getAction(),
                        t.getObservationCount(), t.getLastSeenAt()))
                .toList();
        return new GraphDto(serial, nodes, edges);
    }

    public NodeDto node(Long id) {
        if (id == null || id < 1) throw new BadRequestException("Invalid node id");
        return toDto(nodeRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Screen node not found: " + id)));
    }

    private static PageRequest pageable(int page, int size, Sort sort) {
        if (page < 0) throw new BadRequestException("page must be >= 0");
        if (size < 1 || size > MAX_PAGE_SIZE) throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        return PageRequest.of(page, size, sort);
    }

    // ── Mapping ─────────────────────────────────────────────────────────────

    private SessionDto toDto(ExplorationSession s) {
        AtomicBoolean flag = stopFlags.get(s.getId());
        return new SessionDto(s.getId(), s.getDeviceSerial(), s.getMode(), s.getStatus(), s.getMaxSteps(),
                s.getMaxDurationSeconds(), s.getSettleMs(), s.isAllowSelect(), s.isAllowHome(), s.getStepsTaken(),
                s.getNewScreens(), nodeRef(s.getCurrentNodeId()), s.getCurrentCaptureId(),
                flag != null && flag.get(), s.getStopReason(), s.getStartedAt(), s.getEndedAt());
    }

    private StepDto toDto(ExplorationStep s) {
        Map<Long, String> labels = labels(s.getFromNodeId(), s.getToNodeId());
        return new StepDto(s.getId(), s.getStepIndex(), s.getAction(), s.getStatus(),
                ref(s.getFromNodeId(), labels), ref(s.getToNodeId(), labels), s.getFromCaptureId(), s.getToCaptureId(),
                s.isNewScreen(), s.isSameScreen(), s.isAutoTriggered(), s.getDurationMs(), s.getMessage(), s.getCreatedAt());
    }

    private NodeDto toDto(ScreenNode n) {
        return new NodeDto(n.getId(), n.getLabel(), n.getScreenType(), n.getDescription(), n.getRiskLevel(),
                n.getLabelSource(), n.getInterpretationInput(), n.getInterpretationModel(), n.getInterpretationError(),
                n.getVisitCount(), n.getRepresentativeCaptureId(), "/api/screens/" + n.getRepresentativeCaptureId() + "/image",
                n.getFirstSeenAt(), n.getLastSeenAt());
    }

    private NodeRef nodeRef(Long id) {
        return id == null ? null : nodeRepository.findById(id).map(n -> new NodeRef(n.getId(), n.getLabel())).orElse(null);
    }

    private Map<Long, String> labels(Long... ids) {
        List<Long> present = Arrays.stream(ids).filter(Objects::nonNull).distinct().toList();
        return present.isEmpty() ? Map.of() : nodeRepository.findAllById(present).stream()
                .collect(Collectors.toMap(ScreenNode::getId, ScreenNode::getLabel, (a, b) -> a));
    }

    private static NodeRef ref(Long id, Map<Long, String> labels) {
        return id == null ? null : new NodeRef(id, labels.get(id));
    }
}
