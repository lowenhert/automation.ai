package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.ai.testplan.TestPlanDraft;
import com.lowenhert.patchmanagement.core.ai.testplan.TestPlanValidator;
import com.lowenhert.patchmanagement.core.ai.testplan.TestPlanningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Calls the real Ollama server configured by OLLAMA_BASE_URL / OLLAMA_MODEL.
 * Opt-in because cloud models consume account quota: set OLLAMA_LIVE_TEST=true.
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_LIVE_TEST", matches = "true")
class OllamaLiveTest {

    private final OllamaProperties properties = new OllamaProperties(
            Optional.ofNullable(System.getenv("OLLAMA_BASE_URL")).orElse("http://localhost:11434"),
            Optional.ofNullable(System.getenv("OLLAMA_MODEL")).orElse("gemma4:31b-cloud"),
            5000, 120000, Optional.ofNullable(System.getenv("OLLAMA_API_KEY")).orElse(""), 2);
    private final ObjectMapper mapper = new ObjectMapper();
    private final OllamaClient client = new OllamaClient(properties, mapper);

    @Test
    void healthCheckWithGenerationProbe() {
        AiHealthService.AiHealth health = new AiHealthService(client, properties).check(true);
        assertTrue(health.isUp(), () -> "Ollama health DOWN: " + health.error());
        assertTrue(health.reachable());
        assertTrue(health.modelAvailable());
        assertEquals(Boolean.TRUE, health.generationOk());
        assertNotNull(health.ollamaVersion());
    }

    @Test
    void draftsAValidatedTestPlan() {
        TestPlanningService planner = new TestPlanningService(new StructuredChatService(client, new JsonSchemaValidator()), mapper);
        TestPlanDraft plan = planner.draft("Open Settings and verify that 'Network & internet' is visible",
                "Settings\nNetwork & internet\nConnected devices\nApps");
        assertTrue(TestPlanValidator.validate(plan).isEmpty());
        assertFalse(plan.steps().isEmpty());
        assertTrue(plan.steps().stream().anyMatch(s -> s.action() == TestPlanDraft.Action.ASSERT_TEXT_VISIBLE));
    }

    @Test
    void unknownModelIsReportedAsModelNotFound() {
        OllamaClient other = new OllamaClient(new OllamaProperties(properties.getBaseUrl().toString(),
                "no-such-model-automation-ai:1b", 5000, 30000, properties.getApiKey(), 1), mapper);
        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> other.showModel("no-such-model-automation-ai:1b"));
        assertEquals(AiProviderException.Reason.MODEL_NOT_FOUND, ex.getReason());
    }
}
