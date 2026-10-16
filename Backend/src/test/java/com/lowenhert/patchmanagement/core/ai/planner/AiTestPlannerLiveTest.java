package com.lowenhert.patchmanagement.core.ai.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.ai.JsonSchemaValidator;
import com.lowenhert.patchmanagement.core.ai.OllamaClient;
import com.lowenhert.patchmanagement.core.ai.OllamaProperties;
import com.lowenhert.patchmanagement.core.ai.StructuredChatService;
import com.lowenhert.patchmanagement.core.ai.planner.TestPlannerDtos.*;
import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenNode;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenNodeRepository;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenTransition;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenTransitionRepository;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreenRepository;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenTextRegion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plans against the real model (OLLAMA_MODEL, default gemma4:31b-cloud) using screen knowledge shaped like the
 * Android Settings screens observed on the emulator. Opt-in: OLLAMA_LIVE_TEST=true (uses cloud quota).
 */
@DataJpaTest
@ActiveProfiles("h2")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE_TEST", matches = "true")
class AiTestPlannerLiveTest {

    private static final String SERIAL = "emulator-5554";

    @Autowired private ScreenNodeRepository nodeRepository;
    @Autowired private ScreenTransitionRepository transitionRepository;
    @Autowired private CapturedScreenRepository capturedRepository;
    @Autowired private AiTestPlanRepository planRepository;

    private ScreenNode node(String label, String type, String... texts) {
        String id = UUID.randomUUID().toString();
        CapturedScreen shot = CapturedScreen.builder().captureId(id).deviceSerial(SERIAL).capturedAt(Instant.now())
                .imageFile(id + ".png").width(1080).height(2340).analysisStatus("SUCCESS").textRegionCount(texts.length).build();
        int y = 300;
        for (String t : texts) {
            shot.addTextRegion(ScreenTextRegion.builder().regionIndex(y).regionType("OCR_TEXT").text(t)
                    .confidence(95.0).x(200).y(y).width(400).height(40).build());
            y += 140;
        }
        capturedRepository.save(shot);
        return nodeRepository.save(ScreenNode.builder().deviceSerial(SERIAL).label(label).screenType(type)
                .riskLevel(ScreenNode.RiskLevel.SAFE).representativeCaptureId(id).build());
    }

    private void edge(ScreenNode a, RemoteKey key, ScreenNode b) {
        transitionRepository.save(ScreenTransition.builder().deviceSerial(SERIAL).fromNode(a).action(key).toNode(b)
                .observationCount(1).firstSeenAt(Instant.now()).lastSeenAt(Instant.now()).build());
    }

    @Test
    void generatesValidatedGroundedDraftWithRealModel() {
        ScreenNode home = node("Home Launcher", "HOME", "Thu, Oct 15", "Search");
        ScreenNode settings = node("Settings", "SETTINGS", "Search Settings", "Google", "Network & internet",
                "Connected devices", "Apps", "Notifications", "Sound & vibration", "Display & touch");
        ScreenNode network = node("Network & internet", "SETTINGS", "Internet", "Calls & SMS", "SIMs", "Airplane mode", "Hotspot & tethering");
        edge(settings, RemoteKey.SELECT, network);
        edge(network, RemoteKey.BACK, settings);
        edge(settings, RemoteKey.BACK, home);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        OllamaProperties props = new OllamaProperties(
                Optional.ofNullable(System.getenv("OLLAMA_BASE_URL")).orElse("http://localhost:11434"),
                Optional.ofNullable(System.getenv("OLLAMA_MODEL")).orElse("gemma4:31b-cloud"), 5000, 180000, "", 2);
        AiTestPlannerService planner = new AiTestPlannerService(
                new PlannerKnowledgeService(nodeRepository, transitionRepository, capturedRepository),
                new StructuredChatService(new OllamaClient(props, mapper), new JsonSchemaValidator()),
                planRepository, nodeRepository, props, mapper, 3);

        PlanDetail detail = planner.generate(new GenerateRequest(
                "Starting from Settings, open Network & internet and verify that Airplane mode is listed, then go back to Settings",
                SERIAL, settings.getId(), 10), "live-test");

        System.out.println("Plan " + detail.id() + " '" + detail.title() + "' attempts=" + detail.attempts()
                + " errors=" + detail.errorFlags() + " warnings=" + detail.warningFlags());
        detail.plan().path("steps").forEach(s -> System.out.println("  " + s.path("order") + ". " + s.path("action").asText()
                + " target=" + s.path("target") + " from=" + s.path("fromScreenId") + " to=" + s.path("expectedScreenId")
                + " evidence=" + detail.stepEvidence().get(s.path("order").asInt())));
        detail.flags().forEach(f -> System.out.println("  FLAG " + f.severity() + " " + f.type() + " step=" + f.stepOrder() + ": " + f.message()));

        assertEquals(AiTestPlan.Status.DRAFT, detail.status());
        assertTrue(detail.attempts() >= 1 && detail.attempts() <= 3);
        assertTrue(detail.stepCount() >= 2 && detail.stepCount() <= 10);

        // Every screen id the model used is either known or explicitly flagged as unknown
        Set<Long> known = Set.of(home.getId(), settings.getId(), network.getId());
        Set<Integer> flaggedUnknownSteps = detail.flags().stream()
                .filter(f -> f.type() == PlanReviewer.FlagType.UNKNOWN_SCREEN && f.stepOrder() != null)
                .map(PlanReviewer.Flag::stepOrder).collect(Collectors.toSet());
        for (JsonNode step : detail.plan().path("steps")) {
            for (String field : new String[]{"fromScreenId", "expectedScreenId"}) {
                JsonNode id = step.path(field);
                if (id.isNumber() && !known.contains(id.asLong())) {
                    assertTrue(flaggedUnknownSteps.contains(step.path("order").asInt()), "unknown screen not flagged: " + step);
                }
            }
        }
        // The core navigation is grounded in the observed Settings -SELECT-> Network transition
        assertTrue(detail.stepEvidence().containsValue(PlanReviewer.Evidence.OBSERVED), detail.stepEvidence().toString());
    }
}
