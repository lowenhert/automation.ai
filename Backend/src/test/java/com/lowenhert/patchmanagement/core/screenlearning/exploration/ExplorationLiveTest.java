package com.lowenhert.patchmanagement.core.screenlearning.exploration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.ai.ModelCapabilities;
import com.lowenhert.patchmanagement.core.ai.OllamaClient;
import com.lowenhert.patchmanagement.core.ai.OllamaProperties;
import com.lowenhert.patchmanagement.core.ai.StructuredChatService;
import com.lowenhert.patchmanagement.core.screenlearning.adb.AdbCommandExecutor;
import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import com.lowenhert.patchmanagement.core.screenlearning.adb.ScreenLearningAdbService;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import com.lowenhert.patchmanagement.core.screenlearning.capture.ScreenCaptureService;
import com.lowenhert.patchmanagement.core.screenlearning.capture.ScreenStorage;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ExplorationDtos.*;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreenRepository;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenHistoryService;
import com.lowenhert.patchmanagement.core.screenlearning.process.ProcessRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real end-to-end: emulator (adb) → capture.py + analyzer (OCR, hashes) → identification → Gemma via Ollama
 * (image input) → H2. Opt-in (OLLAMA_LIVE_TEST=true) because it drives the device and uses cloud quota.
 * Only non-destructive keys are sent and the device is returned to HOME afterwards.
 */
@DataJpaTest
@ActiveProfiles("h2")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE_TEST", matches = "true")
class ExplorationLiveTest {

    @Autowired private ExplorationSessionRepository sessionRepository;
    @Autowired private ExplorationStepRepository stepRepository;
    @Autowired private ScreenNodeRepository nodeRepository;
    @Autowired private ScreenTransitionRepository transitionRepository;
    @Autowired private CapturedScreenRepository capturedRepository;

    @TempDir
    Path captureDir;

    @Test
    void manualExplorationOnRealDeviceWithGemmaImageInput() throws Exception {
        String env = Optional.ofNullable(System.getenv("TESSERACT_PATH")).orElse("");
        ProcessRunner runner = new ProcessRunner();
        AdbCommandExecutor adbExec = new AdbCommandExecutor(runner, Optional.ofNullable(System.getenv("ADB_PATH")).orElse("adb"), 15000);
        ScreenLearningAdbService adb = new ScreenLearningAdbService(adbExec);
        String serial = adb.listDevices().stream().filter(d -> "device".equals(d.getState()))
                .map(AdbDeviceDto::getSerial).findFirst().orElse(null);
        assumeTrue(serial != null, "No online ADB device");

        ObjectMapper mapper = new ObjectMapper();
        ScreenStorage storage = new ScreenStorage(captureDir.toString());
        ScreenHistoryService history = new ScreenHistoryService(capturedRepository, storage, mapper);
        ScreenCaptureService capture = new ScreenCaptureService(runner, adbExec, adb, storage, history, mapper,
                "python", "../ai-engine/capture/capture.py", 90000, 15000, env, "eng");
        OllamaProperties ollamaProps = new OllamaProperties(
                Optional.ofNullable(System.getenv("OLLAMA_BASE_URL")).orElse("http://localhost:11434"),
                Optional.ofNullable(System.getenv("OLLAMA_MODEL")).orElse("gemma4:31b-cloud"), 5000, 120000, "", 2);
        OllamaClient ollama = new OllamaClient(ollamaProps, mapper);
        ScreenInterpretationService interpretation = new ScreenInterpretationService(
                new StructuredChatService(ollama, new com.lowenhert.patchmanagement.core.ai.JsonSchemaValidator()),
                new ModelCapabilities(ollama), ollamaProps, mapper, true);
        DestructiveActionGuard guard = new DestructiveActionGuard(DestructiveActionGuard.DEFAULT_KEYWORDS);
        ExplorationStepExecutor executor = new ExplorationStepExecutor(adb, capture, capturedRepository, storage,
                new ScreenIdentifier(nodeRepository, guard), interpretation, guard, nodeRepository, transitionRepository, stepRepository);
        ExplorationService service = new ExplorationService(sessionRepository, stepRepository, nodeRepository,
                transitionRepository, executor, adb, new ExplorationProperties(200, 30, 1800, 600, 1500, 300, 10, 3, 5, 3),
                new com.lowenhert.patchmanagement.core.screenlearning.adb.DeviceReservations());

        // Test setup only: open Settings so the start screen has readable text
        runner.run(List.of(adbExec.getAdbPath(), "-s", serial, "shell", "am", "start", "-a", "android.settings.SETTINGS"), null, 15000);
        Thread.sleep(2500);
        try {
            SessionDto session = service.start(new StartRequest(serial, ExplorationSession.Mode.MANUAL, 5, 300, 1500, null, null), "live");
            ScreenNode start = nodeRepository.findById(session.currentNode().id()).orElseThrow();
            System.out.printf("Start screen: '%s' type=%s risk=%s input=%s error=%s%n", start.getLabel(), start.getScreenType(),
                    start.getRiskLevel(), start.getInterpretationInput(), start.getInterpretationError());
            assertEquals(ScreenNode.LabelSource.LLM, start.getLabelSource(), start.getInterpretationError());
            // The image path must be taken (model reports vision). Whether it is verified depends on the model's
            // wording vs OCR on this run, so both outcomes are legitimate; TEXT/NONE would mean the image was not used.
            assertTrue(Set.of(ScreenNode.InterpretationInput.IMAGE, ScreenNode.InterpretationInput.IMAGE_UNVERIFIED)
                    .contains(start.getInterpretationInput()), "image input was not used: " + start.getInterpretationInput());

            StepDto down = service.act(session.id(), RemoteKey.DPAD_DOWN);
            StepDto back = service.act(session.id(), RemoteKey.BACK);
            System.out.printf("DPAD_DOWN: %s -> %s (same=%s new=%s)%n", down.from().label(), down.to().label(), down.sameScreen(), down.newScreen());
            System.out.printf("BACK: %s -> %s (same=%s new=%s)%n", back.from().label(), back.to().label(), back.sameScreen(), back.newScreen());
            assertEquals(ExplorationStep.Status.OK, down.status(), down.message());
            assertEquals(ExplorationStep.Status.OK, back.status(), back.message());

            GraphDto graph = service.graph(serial);
            assertTrue(graph.nodes().size() >= 1);
            assertEquals(2, graph.edges().stream().mapToInt(EdgeDto::count).sum());
            assertEquals(ExplorationSession.Status.STOPPED, service.stop(session.id()).status());
        } finally {
            adb.sendKey(serial, RemoteKey.HOME);
        }
    }
}
