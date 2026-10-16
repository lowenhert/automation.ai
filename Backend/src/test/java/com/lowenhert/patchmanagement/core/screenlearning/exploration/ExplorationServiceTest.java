package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ConflictException;
import com.lowenhert.patchmanagement.core.config.exception.ServiceUnavailableException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import com.lowenhert.patchmanagement.core.screenlearning.adb.ScreenLearningAdbService;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import com.lowenhert.patchmanagement.core.screenlearning.capture.ScreenCaptureService;
import com.lowenhert.patchmanagement.core.screenlearning.capture.ScreenStorage;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ExplorationDtos.*;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exploration against a simulated TV device, with real identification, guard, persistence (H2) and threading. */
@DataJpaTest
@ActiveProfiles("h2")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // auto-exploration runs on another thread; data must commit
class ExplorationServiceTest {

    private static final String SERIAL = "emulator-5554";

    @Autowired private ExplorationSessionRepository sessionRepository;
    @Autowired private ExplorationStepRepository stepRepository;
    @Autowired private ScreenNodeRepository nodeRepository;
    @Autowired private ScreenTransitionRepository transitionRepository;
    @Autowired private CapturedScreenRepository capturedRepository;

    @TempDir
    Path captureDir;

    private FakeTvDevice device;
    private final java.util.concurrent.atomic.AtomicBoolean failCaptures = new java.util.concurrent.atomic.AtomicBoolean();
    private ScreenCaptureService captureService;
    private ScreenInterpretationService interpretation;
    private ExplorationService service;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        device = new FakeTvDevice();
        ScreenLearningAdbService adb = mock(ScreenLearningAdbService.class);
        when(adb.requireOnlineDevice(SERIAL)).thenReturn(AdbDeviceDto.builder().serial(SERIAL).state("device").build());
        doAnswer(inv -> {
            device.press(inv.getArgument(1));
            return null;
        }).when(adb).sendKey(eq(SERIAL), any());

        captureService = mock(ScreenCaptureService.class);
        failCaptures.set(false);
        when(captureService.capture(SERIAL)).thenAnswer(inv -> {
            if (failCaptures.get()) throw new ServiceUnavailableException("ADB command timed out");
            return device.capture(SERIAL, capturedRepository);
        });

        interpretation = mock(ScreenInterpretationService.class);
        when(interpretation.isEnabled()).thenReturn(true);
        when(interpretation.interpret(any(), any())).thenReturn(ScreenInterpretationService.Result.failed("not configured in test"));

        DestructiveActionGuard guard = new DestructiveActionGuard(DestructiveActionGuard.DEFAULT_KEYWORDS);
        ExplorationStepExecutor executor = new ExplorationStepExecutor(adb, captureService, capturedRepository,
                new ScreenStorage(captureDir.toString()), new ScreenIdentifier(nodeRepository, guard), interpretation,
                guard, nodeRepository, transitionRepository, stepRepository);
        ExplorationProperties limits = new ExplorationProperties(200, 30, 1800, 600, 0, 0, 10, 3, 20, 3);
        service = new ExplorationService(sessionRepository, stepRepository, nodeRepository, transitionRepository,
                executor, adb, limits, new com.lowenhert.patchmanagement.core.screenlearning.adb.DeviceReservations());
    }

    // This class commits (see @Transactional above); other tests share the in-memory database
    @AfterEach
    void cleanDatabase() {
        stepRepository.deleteAll();
        sessionRepository.deleteAll();
        transitionRepository.deleteAll();
        nodeRepository.deleteAll();
        capturedRepository.deleteAll();
    }

    private SessionDto startManual(Integer maxSteps) {
        return service.start(new StartRequest(SERIAL, ExplorationSession.Mode.MANUAL, maxSteps, null, 0, null, null), "tester");
    }

    private SessionDto awaitEnd(Long id) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        SessionDto s = service.get(id);
        while (s.status().isOpen() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            s = service.get(id);
        }
        assertFalse(s.status().isOpen(), "auto session did not finish in time: " + s);
        return s;
    }

    private Map<String, ScreenNode> nodesByLabel() {
        return nodeRepository.findByDeviceSerial(SERIAL).stream().collect(Collectors.toMap(ScreenNode::getLabel, Function.identity()));
    }

    // ── Manual exploration ──────────────────────────────────────────────────

    @Test
    void manualSessionRecordsSourceActionDestinationAndDeduplicates() {
        SessionDto session = startManual(null);
        assertEquals(ExplorationSession.Status.ACTIVE, session.status());
        assertEquals("Home", session.currentNode().label());

        StepDto focus = service.act(session.id(), RemoteKey.DPAD_DOWN);
        assertEquals(ExplorationStep.Status.OK, focus.status());
        assertTrue(focus.sameScreen(), "focus move stays on the same screen");
        assertFalse(focus.newScreen());

        service.act(session.id(), RemoteKey.DPAD_UP);
        StepDto select = service.act(session.id(), RemoteKey.SELECT);
        assertTrue(select.newScreen());
        assertEquals("Home", select.from().label());
        assertEquals("Settings", select.to().label());

        service.act(session.id(), RemoteKey.BACK);
        StepDto again = service.act(session.id(), RemoteKey.SELECT);
        assertFalse(again.newScreen(), "revisited screen is recognised, not duplicated");

        Map<String, ScreenNode> nodes = nodesByLabel();
        assertEquals(2, nodes.size());
        assertEquals(4, nodes.get("Home").getVisitCount(), "start, DOWN, UP, BACK");
        assertEquals(2, nodes.get("Settings").getVisitCount());

        GraphDto graph = service.graph(SERIAL);
        EdgeDto homeToSettings = graph.edges().stream()
                .filter(e -> e.action() == RemoteKey.SELECT && e.from().equals(nodes.get("Home").getId()))
                .findFirst().orElseThrow();
        assertEquals(nodes.get("Settings").getId(), homeToSettings.to());
        assertEquals(2, homeToSettings.count(), "repeated transition increments count instead of duplicating");
        assertEquals(4, graph.edges().size(), "Home-DOWN->Home, Home-UP->Home, Home-SELECT->Settings, Settings-BACK->Home");

        assertEquals(5, service.get(session.id()).stepsTaken());
        assertEquals(5, service.steps(session.id(), 0, 50).totalElements());
        assertEquals(5, service.steps(session.id(), 0, 50).content().get(0).stepIndex(), "history is newest first");
    }

    @Test
    void selectIsBlockedOnDestructiveScreensAndNeverReachesTheDevice() {
        SessionDto session = startManual(null);
        service.act(session.id(), RemoteKey.SELECT);    // Home -> Settings
        service.act(session.id(), RemoteKey.DPAD_DOWN); // focus Device preferences
        StepDto prefs = service.act(session.id(), RemoteKey.SELECT);
        assertEquals("Device preferences", prefs.to().label());
        assertEquals(ScreenNode.RiskLevel.DESTRUCTIVE, nodesByLabel().get("Device preferences").getRiskLevel());

        int keysBefore = device.keysSent.size();
        StepDto blocked = service.act(session.id(), RemoteKey.SELECT);

        assertEquals(ExplorationStep.Status.BLOCKED, blocked.status());
        assertTrue(blocked.message().contains("DESTRUCTIVE"), blocked.message());
        assertEquals(keysBefore, device.keysSent.size(), "blocked SELECT must not be sent");
        assertEquals(3, service.get(session.id()).stepsTaken(), "blocked actions don't consume the step budget");

        assertEquals(ExplorationStep.Status.OK, service.act(session.id(), RemoteKey.BACK).status(), "BACK is still allowed");
    }

    @Test
    void manualSessionStopsAtStepLimit() {
        SessionDto session = startManual(2);
        service.act(session.id(), RemoteKey.DPAD_DOWN);
        service.act(session.id(), RemoteKey.DPAD_DOWN);

        SessionDto after = service.get(session.id());
        assertEquals(ExplorationSession.Status.LIMIT_REACHED, after.status());
        assertThrows(ConflictException.class, () -> service.act(session.id(), RemoteKey.DPAD_DOWN));
        assertEquals(2, device.keysSent.size());
    }

    @Test
    void onlyOneOpenSessionPerDeviceAndStopReleasesIt() {
        SessionDto first = startManual(null);
        assertThrows(ConflictException.class, () -> startManual(null));

        SessionDto stopped = service.stop(first.id());
        assertEquals(ExplorationSession.Status.STOPPED, stopped.status());
        assertNotNull(stopped.endedAt());
        assertThrows(ConflictException.class, () -> service.act(first.id(), RemoteKey.BACK));
        assertEquals(stopped.status(), service.stop(first.id()).status(), "stop is idempotent");

        assertEquals(ExplorationSession.Status.ACTIVE, startManual(null).status());
    }

    @Test
    void boundsAreValidated() {
        assertThrows(BadRequestException.class, () -> startManual(0));
        assertThrows(BadRequestException.class, () -> startManual(201));
        assertThrows(BadRequestException.class, () -> service.start(
                new StartRequest(SERIAL, ExplorationSession.Mode.AUTO, 10, 99_999, 0, true, false), "t"));
        assertThrows(BadRequestException.class, () -> service.start(
                new StartRequest(SERIAL, ExplorationSession.Mode.AUTO, 10, null, 60_000, true, false), "t"));
        assertThrows(BadRequestException.class, () -> service.start(
                new StartRequest(SERIAL, null, 10, null, null, null, null), "t"));
        assertTrue(device.keysSent.isEmpty());
    }

    // ── Auto exploration ────────────────────────────────────────────────────

    @Test
    void autoExplorationIsBoundedDiscoversScreensAndRespectsGuards() throws Exception {
        SessionDto started = service.start(new StartRequest(SERIAL, ExplorationSession.Mode.AUTO, 60, null, 0, true, false), "tester");
        assertEquals(ExplorationSession.Status.RUNNING, started.status());

        SessionDto done = awaitEnd(started.id());

        assertTrue(List.of(ExplorationSession.Status.COMPLETED, ExplorationSession.Status.STALLED,
                ExplorationSession.Status.LIMIT_REACHED).contains(done.status()), done.toString());
        assertTrue(done.stepsTaken() <= 60);
        assertFalse(device.keysSent.contains(RemoteKey.HOME), "HOME not allowed in this session");
        assertTrue(nodesByLabel().keySet().containsAll(List.of("Home", "Settings", "Network & internet", "Device preferences")),
                nodesByLabel().keySet().toString());
        // FakeTvDevice throws if SELECT ever reaches the destructive screen; also check history has no such OK step
        long destructiveSelects = stepRepository.findAll().stream()
                .filter(s -> s.getAction() == RemoteKey.SELECT && s.getStatus() == ExplorationStep.Status.OK)
                .filter(s -> s.getFromNodeId().equals(nodesByLabel().get("Device preferences").getId()))
                .count();
        assertEquals(0, destructiveSelects);
    }

    @Test
    void autoExplorationWithoutSelectNeverPressesSelectOrHome() throws Exception {
        SessionDto started = service.start(new StartRequest(SERIAL, ExplorationSession.Mode.AUTO, 30, null, 0, false, false), "t");
        awaitEnd(started.id());
        assertFalse(device.keysSent.contains(RemoteKey.SELECT));
        assertFalse(device.keysSent.contains(RemoteKey.HOME));
        assertThrows(ConflictException.class, () -> service.act(started.id(), RemoteKey.BACK), "no manual actions on auto sessions");
    }

    @Test
    void stopControlEndsRunningAutoExploration() throws Exception {
        device.keyDelayMs = 100;
        SessionDto started = service.start(new StartRequest(SERIAL, ExplorationSession.Mode.AUTO, 200, null, 0, true, false), "t");
        Thread.sleep(400);
        SessionDto stopping = service.stop(started.id());
        assertTrue(stopping.stopRequested() || !stopping.status().isOpen());

        SessionDto done = awaitEnd(started.id());
        assertEquals(ExplorationSession.Status.STOPPED, done.status());
        assertTrue(done.stepsTaken() < 200);
        int keysAtStop = device.keysSent.size();
        Thread.sleep(300);
        assertEquals(keysAtStop, device.keysSent.size(), "no keys are sent after the session ends");
    }

    @Test
    void repeatedCaptureFailuresFailTheAutoSession() throws Exception {
        SessionDto started = service.start(new StartRequest(SERIAL, ExplorationSession.Mode.AUTO, 50, null, 0, true, false), "t");
        failCaptures.set(true);

        SessionDto done = awaitEnd(started.id());

        assertEquals(ExplorationSession.Status.FAILED, done.status());
        assertTrue(done.stopReason().contains("ADB command timed out"), done.stopReason());
        assertTrue(stepRepository.findAll().stream().anyMatch(s -> s.getStatus() == ExplorationStep.Status.FAILED));
    }

    // ── Interpretation & recovery ───────────────────────────────────────────

    @Test
    void llmInterpretationLabelsNewScreensButCanOnlyEscalateRisk() {
        when(interpretation.interpret(any(), any())).thenAnswer(inv -> {
            var shot = (com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen) inv.getArgument(0);
            boolean prefs = shot.getExtractedText().contains("Factory reset");
            boolean home = shot.getExtractedText().startsWith("Home");
            return new ScreenInterpretationService.Result(new ScreenInterpretationService.Interpretation(
                    prefs ? "Device preferences" : home ? "TV home" : "Settings menu", prefs ? "SETTINGS" : "HOME",
                    "desc", "SAFE", List.of()),
                    ScreenNode.InterpretationInput.IMAGE, "gemma4:31b-cloud", null);
        });

        SessionDto session = startManual(null);
        service.act(session.id(), RemoteKey.SELECT);
        service.act(session.id(), RemoteKey.DPAD_DOWN);
        service.act(session.id(), RemoteKey.SELECT);

        Map<String, ScreenNode> nodes = nodesByLabel();
        ScreenNode home = nodes.get("TV home");
        assertNotNull(home, nodes.keySet().toString());
        assertEquals(ScreenNode.LabelSource.LLM, home.getLabelSource());
        assertEquals(ScreenNode.InterpretationInput.IMAGE, home.getInterpretationInput());
        assertEquals("gemma4:31b-cloud", home.getInterpretationModel());
        assertEquals(ScreenNode.RiskLevel.DESTRUCTIVE, nodes.get("Device preferences").getRiskLevel(),
                "LLM 'SAFE' must not downgrade an OCR-detected destructive screen");
        // Interpretation runs only for new screens (Home, Settings, Device preferences), not for revisits
        verify(interpretation, times(3)).interpret(any(), any());
    }

    @Test
    void openSessionsAreMarkedInterruptedAfterRestart() {
        ExplorationSession orphan = sessionRepository.save(ExplorationSession.builder().deviceSerial(SERIAL)
                .mode(ExplorationSession.Mode.AUTO).status(ExplorationSession.Status.RUNNING).maxSteps(10)
                .maxDurationSeconds(60).settleMs(0).startedAt(Instant.now()).build());

        service.closeInterruptedSessions();

        ExplorationSession reloaded = sessionRepository.findById(orphan.getId()).orElseThrow();
        assertEquals(ExplorationSession.Status.INTERRUPTED, reloaded.getStatus());
        assertNotNull(reloaded.getEndedAt());
    }
}
