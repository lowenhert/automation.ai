package com.lowenhert.patchmanagement.core.ai.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.ai.AiProviderException;
import com.lowenhert.patchmanagement.core.ai.JsonSchemaValidator;
import com.lowenhert.patchmanagement.core.ai.BedrockClient;
import com.lowenhert.patchmanagement.core.ai.BedrockProperties;
import com.lowenhert.patchmanagement.core.ai.StructuredChatService;
import com.lowenhert.patchmanagement.core.ai.planner.TestPlannerDtos.*;
import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.config.exception.ConflictException;
import com.lowenhert.patchmanagement.core.screenlearning.adb.RemoteKey;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenNode;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenNodeRepository;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenTransition;
import com.lowenhert.patchmanagement.core.screenlearning.exploration.ScreenTransitionRepository;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreen;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreenRepository;
import com.lowenhert.patchmanagement.core.screenlearning.history.ScreenTextRegion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Planner end-to-end with real retrieval, schema validation, review and persistence (H2); only bedrock is scripted. */
@DataJpaTest
@ActiveProfiles("h2")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AiTestPlannerServiceTest {

    private static final String SERIAL = "emulator-5554";

    @Autowired private ScreenNodeRepository nodeRepository;
    @Autowired private ScreenTransitionRepository transitionRepository;
    @Autowired private CapturedScreenRepository capturedRepository;
    @Autowired private AiTestPlanRepository planRepository;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private BedrockClient bedrock;
    private AiTestPlannerService planner;
    private ScreenNode home, settings, network, prefs;

    @BeforeEach
    void setUp() {
        home = node("Home Launcher", "SAFE", "Settings", "Apps", "Movies");
        settings = node("Settings", "SAFE", "Search Settings", "Network & internet", "Device preferences");
        network = node("Network & internet", "SAFE", "Wi-Fi", "Ethernet", "Proxy");
        prefs = node("Device preferences", "DESTRUCTIVE", "Factory reset", "About");
        edge(home, RemoteKey.SELECT, settings);
        edge(settings, RemoteKey.SELECT, network);
        edge(network, RemoteKey.BACK, settings);

        bedrock = mock(BedrockClient.class);
        planner = newPlanner(3);
    }

    private AiTestPlannerService newPlanner(int maxAttempts) {
        return new AiTestPlannerService(new PlannerKnowledgeService(nodeRepository, transitionRepository, capturedRepository),
                new StructuredChatService(bedrock, new JsonSchemaValidator()), planRepository, nodeRepository,
                new BedrockProperties("https://bedrock-mantle.us-east-1.api.aws/v1", "deepseek.v3.2", "", 1000, 1000, 1), mapper, maxAttempts, org.mockito.Mockito.mock(com.lowenhert.patchmanagement.core.testlibrary.TestCaseService.class), org.mockito.Mockito.mock(com.lowenhert.patchmanagement.core.testlibrary.TestScriptService.class), org.mockito.Mockito.mock(com.lowenhert.patchmanagement.core.testlibrary.ScriptExecutionService.class), org.mockito.Mockito.mock(com.lowenhert.patchmanagement.core.screenlearning.adb.ScreenLearningAdbService.class));
    }

    private ScreenNode node(String label, String risk, String... texts) {
        String captureId = UUID.randomUUID().toString();
        CapturedScreen shot = CapturedScreen.builder().captureId(captureId).deviceSerial(SERIAL).capturedAt(Instant.now())
                .imageFile(captureId + ".png").width(1080).height(2340).analysisStatus("SUCCESS").textRegionCount(texts.length)
                .extractedText(String.join("\n", texts)).build();
        shot.addTextRegion(ScreenTextRegion.builder().regionIndex(0).regionType("OCR_TEXT").text("12:49")
                .confidence(90.0).x(10).y(20).width(80).height(30).build()); // status bar, must be excluded
        int y = 300;
        for (String t : texts) {
            shot.addTextRegion(ScreenTextRegion.builder().regionIndex(y).regionType("OCR_TEXT").text(t)
                    .confidence(95.0).x(100).y(y).width(400).height(40).build());
            y += 120;
        }
        capturedRepository.save(shot);
        return nodeRepository.save(ScreenNode.builder().deviceSerial(SERIAL).label(label).screenType("SETTINGS")
                .riskLevel(ScreenNode.RiskLevel.valueOf(risk)).representativeCaptureId(captureId).build());
    }

    private void edge(ScreenNode from, RemoteKey action, ScreenNode to) {
        Instant now = Instant.now();
        transitionRepository.save(ScreenTransition.builder().deviceSerial(SERIAL).fromNode(from).action(action).toNode(to)
                .observationCount(2).firstSeenAt(now).lastSeenAt(now).build());
    }

    private static BedrockClient.ChatResponse reply(String content) {
        return new BedrockClient.ChatResponse(BedrockClient.ChatMessage.assistant(content), 1, 1, "stop");
    }

    private String validPlan() {
        return """
                ```json
                {"title":"Open Wi-Fi settings","summary":"Navigate from home to network settings.",
                 "preconditions":[{"description":"Device is on the home screen","screenId":%d}],
                 "steps":[
                  {"order":1,"action":"SELECT","fromScreenId":%d,"expectedScreenId":%d,"target":"Settings","expectedState":"Settings menu opens","timeoutMs":5000,"onFailure":"RECOVER_HOME"},
                  {"order":2,"action":"SELECT","fromScreenId":%d,"expectedScreenId":%d,"target":"Network & internet","expectedState":"Network screen opens","timeoutMs":5000,"onFailure":"RETRY"},
                  {"order":3,"action":"ASSERT_TEXT_VISIBLE","fromScreenId":%d,"expectedScreenId":%d,"target":"Wi-Fi","expectedState":"Wi-Fi option visible","timeoutMs":3000,"onFailure":"ABORT"}],
                 "assertions":[{"afterStep":3,"type":"SCREEN_IS","screenId":%d,"text":null,"description":"On network screen"}],
                 "failureHandling":{"maxRetries":1,"recovery":"HOME","notes":"Return home on failure"},
                 "assumptions":["Focus starts on the Settings tile"]}
                ```""".formatted(home.getId(), home.getId(), settings.getId(), settings.getId(), network.getId(),
                network.getId(), network.getId(), network.getId());
    }

    private GenerateRequest request(String goal) {
        return new GenerateRequest(goal, SERIAL, home.getId(), 10);
    }

    @Test
    void generatesGroundedDraftAndSavesCanonicalJson() {
        when(bedrock.chat(anyList(), any(), anyMap())).thenReturn(reply(validPlan()));

        PlanDetail detail = planner.generate(request("Verify that Wi-Fi settings can be opened from home"), "alice");

        assertEquals(AiTestPlan.Status.DRAFT, detail.status());
        assertEquals("Open Wi-Fi settings", detail.title());
        assertEquals(3, detail.stepCount());
        assertEquals(1, detail.attempts());
        assertEquals(0, detail.errorFlags());
        assertEquals(0, detail.warningFlags(), detail.flags().toString());
        assertEquals(PlanReviewer.Evidence.OBSERVED, detail.stepEvidence().get(1));
        assertEquals("alice", detail.createdBy());
        assertTrue(detail.note().contains("not been executed"));
        assertEquals("gemma4:31b-cloud", detail.model());
        assertEquals(4, detail.knowledge().path("totalScreens").asInt());

        AiTestPlan stored = planRepository.findById(detail.id()).orElseThrow();
        assertFalse(stored.getPlanJson().contains("```"), "stored plan is canonical JSON, not raw model text");
        assertEquals(3, detail.plan().path("steps").size());
    }

    @Test
    void promptContainsOnlyRealObservationsMarkedAsData() {
        when(bedrock.chat(anyList(), any(), anyMap())).thenReturn(reply(validPlan()));
        planner.generate(request("Open network settings"), "alice");

        verify(bedrock).chat(argThat(messages -> {
            String system = messages.get(0).content();
            String user = messages.get(1).content();
            return system.contains("never claim that anything") && system.contains("\"enum\"")
                    && user.contains("Network & internet") && user.contains("\"risk\":\"DESTRUCTIVE\"")
                    && user.contains("-SELECT->") && user.contains("<<<") && !user.contains("12:49");
        }), any(JsonNode.class), anyMap());
    }

    @Test
    void malformedOutputIsRepairedWithSchemaErrors() {
        String invented = """
                {"title":"x","summary":"","preconditions":[],"steps":[{"order":1,"action":"SWIPE_LEFT","fromScreenId":1,
                 "expectedScreenId":2,"target":null,"expectedState":"s","timeoutMs":5000,"onFailure":"ABORT"}],
                 "assertions":[],"failureHandling":{"maxRetries":0,"recovery":"NONE","notes":""},"assumptions":[],
                 "result":"PASSED"}""";
        when(bedrock.chat(anyList(), any(), anyMap())).thenReturn(reply(invented), reply(validPlan()));

        PlanDetail detail = planner.generate(request("Open network settings"), "alice");

        assertEquals(2, detail.attempts());
        verify(bedrock).chat(argThat(m -> m.size() == 4 && m.get(3).content().contains("schema violation")
                && m.get(3).content().contains("result")), any(), anyMap());
    }

    @Test
    void retriesAreBoundedAndNothingIsSavedWhenOutputNeverValidates() {
        when(bedrock.chat(anyList(), any(), anyMap())).thenReturn(reply("I cannot help with that."));
        long before = planRepository.count();

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> planner.generate(request("Open network settings"), "alice"));

        assertEquals(AiProviderException.Reason.INVALID_OUTPUT, ex.getReason());
        assertTrue(ex.getMessage().contains("3 attempt(s)"));
        verify(bedrock, times(3)).chat(anyList(), any(), anyMap());
        assertEquals(before, planRepository.count());

        reset(bedrock);
        when(bedrock.chat(anyList(), any(), anyMap())).thenReturn(reply("{}"));
        assertThrows(AiProviderException.class, () -> newPlanner(1).generate(request("Open network settings"), "a"));
        verify(bedrock, times(1)).chat(anyList(), any(), anyMap());
    }

    @Test
    void ungroundedPlanIsSavedWithFlagsAndCannotBeApproved() {
        String risky = """
                {"title":"Reset device","summary":"","preconditions":[],"steps":[
                 {"order":1,"action":"SELECT","fromScreenId":%d,"expectedScreenId":99999,"unknownScreenDescription":null,"target":"Factory reset","expectedState":"Reset dialog","timeoutMs":5000,"onFailure":"ABORT"},
                 {"order":2,"action":"UNSUPPORTED","requestedAction":"Enter PIN 0000","fromScreenId":null,"expectedScreenId":null,"target":null,"expectedState":"PIN accepted","timeoutMs":5000,"onFailure":"ABORT"}],
                 "assertions":[],"failureHandling":{"maxRetries":0,"recovery":"NONE","notes":""},"assumptions":["PIN is 0000"]}"""
                .formatted(prefs.getId());
        when(bedrock.chat(anyList(), any(), anyMap())).thenReturn(reply(risky));

        PlanDetail detail = planner.generate(new GenerateRequest("Factory reset the device", SERIAL, null, 10), "alice");

        assertEquals(AiTestPlan.Status.DRAFT, detail.status());
        assertEquals(2, detail.errorFlags(), detail.flags().toString());   // destructive SELECT + unsupported action
        List<PlanReviewer.FlagType> types = detail.flags().stream().map(PlanReviewer.Flag::type).toList();
        assertTrue(types.containsAll(List.of(PlanReviewer.FlagType.DESTRUCTIVE_STEP, PlanReviewer.FlagType.UNSUPPORTED_ACTION,
                PlanReviewer.FlagType.UNKNOWN_SCREEN)), types.toString());

        assertThrows(ConflictException.class,
                () -> planner.review(detail.id(), new ReviewRequest(AiTestPlan.Status.APPROVED, "ok"), "bob"));
        PlanDetail rejected = planner.review(detail.id(), new ReviewRequest(AiTestPlan.Status.REJECTED, "Destructive"), "bob");
        assertEquals(AiTestPlan.Status.REJECTED, rejected.status());
        assertEquals("bob", rejected.reviewedBy());
        assertNotNull(rejected.reviewedAt());
    }

    @Test
    void cleanPlanCanBeApprovedAndListed() {
        when(bedrock.chat(anyList(), any(), anyMap())).thenReturn(reply(validPlan()));
        PlanDetail detail = planner.generate(request("Open network settings"), "alice");

        assertEquals(AiTestPlan.Status.APPROVED,
                planner.review(detail.id(), new ReviewRequest(AiTestPlan.Status.APPROVED, " looks right "), "bob").status());
        assertEquals("looks right", planner.get(detail.id()).reviewNotes());
        assertEquals(1, planner.list(0, 10, null, AiTestPlan.Status.APPROVED).totalElements());
        assertEquals(1, planner.list(0, 10, SERIAL, null).totalElements());
        assertEquals(0, planner.list(0, 10, "other-device", null).totalElements());
    }

    @Test
    void inputIsValidatedBeforeCallingTheModel() {
        assertThrows(BadRequestException.class, () -> planner.generate(request("hi"), "a"));
        assertThrows(BadRequestException.class, () -> planner.generate(request("x".repeat(1001)), "a"));
        assertThrows(BadRequestException.class, () -> planner.generate(new GenerateRequest("Open settings", "-s", null, 10), "a"));
        assertThrows(BadRequestException.class, () -> planner.generate(new GenerateRequest("Open settings", SERIAL, null, 41), "a"));
        assertThrows(BadRequestException.class, () -> planner.generate(new GenerateRequest("Open settings", "other-device", home.getId(), 10), "a"));
        assertThrows(BadRequestException.class, () -> planner.review(1L, new ReviewRequest(null, null), "a"));
        verifyNoInteractions(bedrock);
    }

    @Test
    void unexploredDeviceStillPlansButSaysSo() {
        when(bedrock.chat(anyList(), any(), anyMap())).thenReturn(reply("""
                {"title":"Open settings","summary":"","preconditions":[{"description":"Home screen","screenId":null}],
                 "steps":[{"order":1,"action":"HOME","fromScreenId":null,"expectedScreenId":null,"unknownScreenDescription":"Home screen",
                 "target":null,"expectedState":"Home shown","timeoutMs":5000,"onFailure":"ABORT"}],
                 "assertions":[],"failureHandling":{"maxRetries":0,"recovery":"NONE","notes":""},"assumptions":[]}"""));

        PlanDetail detail = planner.generate(new GenerateRequest("Open settings", "192.168.1.20:5555", null, 5), "a");

        assertEquals(PlanReviewer.FlagType.NO_SCREEN_KNOWLEDGE, detail.flags().get(0).type());
        verify(bedrock).chat(argThat(m -> m.get(1).content().contains("has not been explored")), any(), anyMap());
    }
}


