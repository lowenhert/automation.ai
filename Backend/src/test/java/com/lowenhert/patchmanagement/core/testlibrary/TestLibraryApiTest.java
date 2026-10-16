package com.lowenhert.patchmanagement.core.testlibrary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.ai.planner.AiTestPlan;
import com.lowenhert.patchmanagement.core.ai.planner.AiTestPlanRepository;
import com.lowenhert.patchmanagement.core.ai.planner.GeneratedTestPlan;
import com.lowenhert.patchmanagement.core.auth.JwtTokenUtil;
import com.lowenhert.patchmanagement.core.screenlearning.adb.ScreenLearningAdbService;
import com.lowenhert.patchmanagement.core.screenlearning.adb.dto.AdbDeviceDto;
import com.lowenhert.patchmanagement.core.user.User;
import com.lowenhert.patchmanagement.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test library HTTP API with real persistence (H2), the real Python validator and real script processes. The device
 * lookup is mocked; the scripts used here do not send adb commands, so no device is needed.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testlib-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.test-engine.executions-dir=target/test-executions"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class TestLibraryApiTest {

    static final String SERIAL = "emulator-5554";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private AiTestPlanRepository planRepository;
    @Autowired private ScriptExecutionRepository executionRepository;
    @Autowired private TestEngineSettings settings;
    @Autowired private ScriptExecutionService executionService;
    @MockBean private ScreenLearningAdbService adbService;

    private String token;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("tester").isEmpty()) {
            User user = new User();
            user.setUsername("tester");
            user.setPassword("not-used");
            user.setRole("USER");
            userRepository.save(user);
        }
        token = "Bearer " + jwtTokenUtil.generateToken("tester");
        when(adbService.requireOnlineDevice(anyString()))
                .thenReturn(AdbDeviceDto.builder().serial(SERIAL).state("device").build());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private JsonNode call(MockHttpServletRequestBuilder req, Object body, int expectedStatus) throws Exception {
        req.header("Authorization", token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String content = mvc.perform(req).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return content.isBlank() ? null : json.readTree(content);
    }

    private JsonNode approvedScript(String name, String content) throws Exception {
        JsonNode script = call(post("/api/test-scripts"), Map.of("name", name, "language", "PYTEST", "content", content), 200);
        long id = script.get("id").asLong();
        JsonNode validated = call(post("/api/test-scripts/" + id + "/validate"), null, 200);
        assertThat(validated.at("/validation/valid").asBoolean()).as(validated.toString()).isTrue();
        return call(post("/api/test-scripts/" + id + "/approve"), Map.of("contentHash", script.get("contentHash").asText()), 200);
    }

    private JsonNode execute(JsonNode script, Integer timeout) throws Exception {
        return call(post("/api/executions"), Map.of("scriptId", script.get("id").asLong(), "deviceId", SERIAL,
                "contentHash", script.get("contentHash").asText(), "confirm", true, "timeoutSeconds", timeout), 200);
    }

    private JsonNode awaitFinal(long executionId, long maxMs) throws Exception {
        long end = System.currentTimeMillis() + maxMs;
        JsonNode e;
        do {
            Thread.sleep(250);
            e = call(get("/api/executions/" + executionId), null, 200);
        } while ("RUNNING".equals(e.get("status").asText()) && System.currentTimeMillis() < end);
        return e;
    }

    // ── tests ───────────────────────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/test-cases")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test-scripts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/executions").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/executions/1/artifacts/a.png")).andExpect(status().isUnauthorized());
    }

    @Test
    void caseLifecycleWithEventsDuplicateAndGeneratedDraftScript() throws Exception {
        JsonNode created = call(post("/api/test-cases"), Map.of("name", "Open settings", "description", "Goal"), 200);
        long caseId = created.get("id").asLong();

        call(put("/api/test-cases/" + caseId + "/events"), List.of(Map.of("action", "ASSERT_TEXT_VISIBLE")), 400);
        call(put("/api/test-cases/" + caseId + "/events"), List.of(Map.of("action", "WAIT", "timeoutMs", 999_999)), 400);
        JsonNode withEvents = call(put("/api/test-cases/" + caseId + "/events"), List.of(
                Map.of("action", "HOME"),
                Map.of("action", "DPAD_DOWN", "target", "ignored for keys"),
                Map.of("action", "ASSERT_TEXT_VISIBLE", "target", "Settings", "timeoutMs", 5000)), 200);
        assertThat(withEvents.get("events")).hasSize(3);
        assertThat(withEvents.at("/events/1/target").isNull()).isTrue();

        long secondEvent = withEvents.at("/events/1/id").asLong();
        JsonNode afterDelete = call(delete("/api/test-cases/" + caseId + "/events/" + secondEvent), null, 200);
        assertThat(afterDelete.get("events")).hasSize(2);
        assertThat(afterDelete.at("/events/1/position").asInt()).isEqualTo(2);

        call(post("/api/test-cases/" + caseId + "/rename"), Map.of("name", "  "), 400);
        assertThat(call(post("/api/test-cases/" + caseId + "/rename"), Map.of("name", "Settings smoke"), 200)
                .get("name").asText()).isEqualTo("Settings smoke");

        JsonNode generated = call(post("/api/test-cases/" + caseId + "/scripts/generate"), Map.of("language", "ROBOT"), 200);
        assertThat(generated.get("status").asText()).isEqualTo("DRAFT");
        assertThat(generated.get("origin").asText()).isEqualTo("GENERATED");
        assertThat(generated.get("executable").asBoolean()).isFalse();
        assertThat(generated.get("content").asText()).contains("Text Should Be Visible    Settings    timeout=5.0");
        assertThat(executionRepository.findByScriptIdIn(List.of(generated.get("id").asLong()))).isEmpty();

        JsonNode copy = call(post("/api/test-cases/" + caseId + "/duplicate"), null, 200);
        assertThat(copy.get("name").asText()).isEqualTo("Settings smoke (copy)");
        assertThat(copy.get("events")).hasSize(2);
        assertThat(copy.get("scripts")).hasSize(1);
        assertThat(copy.at("/scripts/0/status").asText()).isEqualTo("DRAFT");
        assertThat(copy.at("/scripts/0/id").asLong()).isNotEqualTo(generated.get("id").asLong());

        JsonNode impact = call(get("/api/test-cases/" + caseId + "/delete-impact"), null, 200);
        assertThat(impact.get("events").asInt()).isEqualTo(2);
        assertThat(impact.get("scripts").asInt()).isEqualTo(1);
        call(delete("/api/test-cases/" + caseId), null, 400);
        call(delete("/api/test-cases/" + caseId).param("confirm", "true"), null, 204);
        call(get("/api/test-cases/" + caseId), null, 404);
        call(get("/api/test-scripts/" + generated.get("id").asLong()), null, 404);
        call(get("/api/test-cases/" + copy.get("id").asLong()), null, 200);
    }

    @Test
    void onlyValidatedApprovedAndConfirmedContentCanRun() throws Exception {
        String content = "def test_ok(device):\n    device.sleep(0)\n";
        JsonNode draft = call(post("/api/test-scripts"), Map.of("name", "Rules", "language", "PYTEST", "content", content), 200);
        long id = draft.get("id").asLong();
        String hash = draft.get("contentHash").asText();
        Map<String, Object> run = Map.of("scriptId", id, "deviceId", SERIAL, "contentHash", hash, "confirm", true);

        call(post("/api/executions"), run, 400);                                        // not approved
        call(post("/api/test-scripts/" + id + "/approve"), Map.of("contentHash", hash), 400); // not validated
        call(post("/api/test-scripts/" + id + "/validate"), null, 200);
        call(post("/api/test-scripts/" + id + "/approve"), Map.of("contentHash", "0".repeat(64)), 409);
        JsonNode approved = call(post("/api/test-scripts/" + id + "/approve"), Map.of("contentHash", hash), 200);
        assertThat(approved.get("executable").asBoolean()).isTrue();
        assertThat(approved.get("approvedBy").asText()).isEqualTo("tester");

        call(post("/api/executions"), Map.of("scriptId", id, "deviceId", SERIAL, "contentHash", hash), 400);  // no confirm
        call(post("/api/executions"), Map.of("scriptId", id, "deviceId", SERIAL, "contentHash", "x", "confirm", true), 409);
        call(post("/api/executions"), Map.of("scriptId", id, "deviceId", SERIAL, "contentHash", hash, "confirm", true,
                "timeoutSeconds", 99_999), 400);

        // Editing returns the script to DRAFT and drops the validation
        JsonNode edited = call(put("/api/test-scripts/" + id), Map.of("content", content + "\n# changed\n"), 200);
        assertThat(edited.get("status").asText()).isEqualTo("DRAFT");
        assertThat(edited.get("executable").asBoolean()).isFalse();
        assertThat(edited.get("validation").isNull()).isTrue();
        call(post("/api/executions"), Map.of("scriptId", id, "deviceId", SERIAL,
                "contentHash", edited.get("contentHash").asText(), "confirm", true), 400);

        // Invalid code can be saved but never approved
        JsonNode bad = call(put("/api/test-scripts/" + id), Map.of("content", "import os\ndef test_x(device):\n    os.system('x')\n"), 200);
        JsonNode badValidation = call(post("/api/test-scripts/" + id + "/validate"), null, 200);
        assertThat(badValidation.at("/validation/valid").asBoolean()).isFalse();
        call(post("/api/test-scripts/" + id + "/approve"), Map.of("contentHash", bad.get("contentHash").asText()), 400);
        call(delete("/api/test-scripts/" + id).param("confirm", "true"), null, 204);
    }

    @Test
    void executesApprovedScriptAndReportsResults() throws Exception {
        JsonNode script = approvedScript("Results", """
                def test_device_selected(device):
                    assert device.serial == "emulator-5554"

                def test_fails(device):
                    print("hello from the test")
                    assert 1 == 2, "expected failure"
                """);
        JsonNode started = execute(script, 120);
        assertThat(started.get("status").asText()).isEqualTo("RUNNING");
        JsonNode done = awaitFinal(started.get("id").asLong(), 90_000);

        assertThat(done.get("status").asText()).as(done.toString()).isEqualTo("FAILED");
        assertThat(done.get("testsTotal").asInt()).isEqualTo(2);
        assertThat(done.get("testsPassed").asInt()).isEqualTo(1);
        assertThat(done.get("testsFailed").asInt()).isEqualTo(1);
        assertThat(done.get("errorMessage").asText()).contains("expected failure");
        assertThat(done.get("contentHash").asText()).isEqualTo(script.get("contentHash").asText());

        long execId = done.get("id").asLong();
        JsonNode log = call(get("/api/executions/" + execId + "/log"), null, 200);
        assertThat(log.get("text").asText()).contains("test_fails");
        assertThat(log.get("complete").asBoolean()).isTrue();
        call(get("/api/executions/" + execId + "/log").param("offset", "-1"), null, 400);
        call(get("/api/executions/" + execId + "/artifacts/evil.txt"), null, 400);
        call(get("/api/executions/" + execId + "/artifacts/..png"), null, 400);
        call(get("/api/executions/" + execId + "/artifacts/missing.png"), null, 404);

        // The backend environment (DB password, JWT secret) is not passed to the script
        var env = new java.util.HashMap<String, String>();
        env.put("SPRING_DATASOURCE_PASSWORD", "secret");
        executionService.configureEnvironment(env, SERIAL, settings.getExecutionsDir().resolve("env-check"));
        assertThat(env).doesNotContainKey("SPRING_DATASOURCE_PASSWORD").containsEntry("AUTOMATION_DEVICE_ID", SERIAL)
                .containsEntry("PYTHONNOUSERSITE", "1");

        long scriptId = script.get("id").asLong();
        JsonNode impact = call(get("/api/test-scripts/" + scriptId + "/delete-impact"), null, 200);
        assertThat(impact.get("executions").asInt()).isEqualTo(1);
        call(delete("/api/test-scripts/" + scriptId), null, 400);
        call(delete("/api/test-scripts/" + scriptId).param("confirm", "true"), null, 204);
        call(get("/api/executions/" + execId), null, 404);
        assertThat(Files.exists(settings.getExecutionsDir().resolve(String.valueOf(execId)))).isFalse();
    }

    @Test
    void stopTimeoutDeviceLockAndDeleteWhileRunning() throws Exception {
        JsonNode script = approvedScript("Slow", "import time\n\ndef test_slow(device):\n    time.sleep(120)\n");
        JsonNode first = execute(script, 120);
        long firstId = first.get("id").asLong();

        call(post("/api/executions"), Map.of("scriptId", script.get("id").asLong(), "deviceId", SERIAL,
                "contentHash", script.get("contentHash").asText(), "confirm", true), 409);  // device busy
        call(delete("/api/test-scripts/" + script.get("id").asLong()).param("confirm", "true"), null, 409);
        call(delete("/api/executions/" + firstId).param("confirm", "true"), null, 409);

        Thread.sleep(1500);
        call(post("/api/executions/" + firstId + "/stop"), null, 200);
        JsonNode stopped = awaitFinal(firstId, 20_000);
        assertThat(stopped.get("status").asText()).isEqualTo("STOPPED");

        JsonNode second = execute(script, 10);   // device released after stop
        JsonNode timedOut = awaitFinal(second.get("id").asLong(), 30_000);
        assertThat(timedOut.get("status").asText()).isEqualTo("TIMED_OUT");

        call(delete("/api/executions/" + firstId), null, 400);
        call(delete("/api/executions/" + firstId).param("confirm", "true"), null, 204);
        call(delete("/api/test-scripts/" + script.get("id").asLong()).param("confirm", "true"), null, 204);
    }

    @Test
    void createsTestCaseFromApprovedPlanOnly() throws Exception {
        GeneratedTestPlan plan = new GeneratedTestPlan("Open Wi-Fi", "summary", List.of(),
                List.of(new GeneratedTestPlan.Step(1, GeneratedTestPlan.Action.HOME, "home", null, null, null, null, "Home screen", 3000, GeneratedTestPlan.OnFailure.ABORT),
                        new GeneratedTestPlan.Step(2, GeneratedTestPlan.Action.WAIT, "wait", null, null, null, null, null, 2000, GeneratedTestPlan.OnFailure.ABORT),
                        new GeneratedTestPlan.Step(3, GeneratedTestPlan.Action.ASSERT_SCREEN, "check", null, 987654L, null, null, "Settings", 5000, GeneratedTestPlan.OnFailure.ABORT)),
                List.of(new GeneratedTestPlan.Assertion(1, GeneratedTestPlan.AssertionType.TEXT_VISIBLE, null, "Apps", "Apps visible")),
                new GeneratedTestPlan.FailureHandling(0, GeneratedTestPlan.Recovery.NONE, null), List.of());
        AiTestPlan saved = planRepository.save(AiTestPlan.builder().title("Open Wi-Fi").goal("Open Wi-Fi settings")
                .deviceSerial(SERIAL).status(AiTestPlan.Status.DRAFT).stepCount(3).maxSteps(10)
                .planJson(json.writeValueAsString(plan)).reviewJson("{}").knowledgeJson("{}")
                .errorFlags(0).warningFlags(0).attempts(1).build());

        call(post("/api/test-cases/from-plan"), Map.of("planId", saved.getId()), 400);   // draft plans are refused
        saved.setStatus(AiTestPlan.Status.APPROVED);
        planRepository.save(saved);

        JsonNode created = call(post("/api/test-cases/from-plan"), Map.of("planId", saved.getId()), 200);
        assertThat(created.get("sourcePlanId").asLong()).isEqualTo(saved.getId());
        assertThat(created.get("events")).extracting(e -> e.get("action").asText())
                .containsExactly("HOME", "ASSERT_TEXT_VISIBLE", "WAIT", "SCREENSHOT", "SCREENSHOT");
        // Unknown screen (no OCR text) is documented by a screenshot instead of an invented assertion
        assertThat(created.at("/events/3/expectedState").asText()).contains("Not verified automatically");
        call(delete("/api/test-cases/" + created.get("id").asLong()).param("confirm", "true"), null, 204);
        planRepository.deleteById(saved.getId());
    }
}
