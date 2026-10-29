package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.ai.testplan.TestPlanDraft;
import com.lowenhert.patchmanagement.core.ai.testplan.TestPlanValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StructuredChatServiceTest {

    // Verbatim shape returned by gemma4:31b-cloud during setup: valid JSON wrapped in a Markdown fence
    private static final String FENCED_VALID = """
            ```json
            {
              "title": "Verify Network & Internet visibility in Settings",
              "steps": [
                {"action": "PRESS_KEY", "target": "HOME", "expected": "Home screen is displayed"},
                {"action": "TAP", "target": "Settings", "expected": "Settings menu is opened"},
                {"action": "ASSERT_TEXT_VISIBLE", "target": "Network & internet", "expected": "Option is visible"}
              ]
            }
            ```""";

    // Also observed: the model ignored the schema and invented actions/fields
    private static final String WRONG_SHAPE = """
            ```json
            [{"step": 1, "action": "click", "target": "Settings icon", "description": "Open settings"}]
            ```""";

    private BedrockClient client;
    private StructuredChatService service;
    private JsonNode schema;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(BedrockClient.class);
        service = new StructuredChatService(client, new JsonSchemaValidator());
        schema = new ObjectMapper().readTree(getClass().getResourceAsStream("/ai/test-plan-draft.schema.json"));
    }

    private static BedrockClient.ChatResponse reply(String content) {
        return new BedrockClient.ChatResponse(BedrockClient.ChatMessage.assistant(content), 1, 1, "stop");
    }

    private TestPlanDraft draft() {
        return service.chatJson("Plan tests.", "Open settings", schema, TestPlanDraft.class, TestPlanValidator::validate);
    }

    @Test
    void acceptsFencedJsonFromRealModelOutput() {
        when(client.chat(anyList(), any(), anyMap())).thenReturn(reply(FENCED_VALID));

        TestPlanDraft plan = draft();

        assertEquals(3, plan.steps().size());
        assertEquals(TestPlanDraft.Action.ASSERT_TEXT_VISIBLE, plan.steps().get(2).action());
        verify(client, times(1)).chat(argThat(m -> m.get(0).content().contains("\"enum\"")), eq(schema), anyMap());
    }

    @Test
    void wrongShapeTriggersOneRepairWithErrorsThenSucceeds() {
        when(client.chat(anyList(), any(), anyMap())).thenReturn(reply(WRONG_SHAPE), reply(FENCED_VALID));

        TestPlanDraft plan = draft();

        assertEquals(3, plan.steps().size());
        verify(client, times(2)).chat(anyList(), any(), anyMap());
        verify(client).chat(argThat(m -> m.size() == 4
                && m.get(2).role().equals("assistant")
                && m.get(3).content().contains("did not contain a JSON object")), any(), anyMap());
    }

    @Test
    void persistentlyInvalidOutputIsRejected() {
        when(client.chat(anyList(), any(), anyMap())).thenReturn(reply("Sure! Here is your plan: step 1, tap settings."));
        AiProviderException ex = assertThrows(AiProviderException.class, this::draft);
        assertEquals(AiProviderException.Reason.INVALID_OUTPUT, ex.getReason());
        verify(client, times(2)).chat(anyList(), any(), anyMap());
    }

    @Test
    void rejectsUnknownEnumUnknownFieldsTrailingDataAndRuleViolations() {
        assertInvalid("{\"title\":\"t\",\"steps\":[{\"action\":\"click\",\"target\":\"x\",\"expected\":\"y\"}]}", "schema violation");
        assertInvalid("{\"title\":\"t\",\"steps\":[{\"action\":\"TAP\",\"target\":\"x\",\"expected\":\"y\",\"script\":\"rm -rf\"}]}", "script");
        // Two objects are ambiguous; trailing tokens are rejected rather than silently picking one
        assertInvalid("{\"title\":\"t\",\"steps\":[{\"action\":\"TAP\",\"target\":\"x\",\"expected\":\"y\"}]} {\"title\":\"u\"}", "not valid JSON");
        assertInvalid("{\"title\":\"t\",\"title\":\"u\",\"steps\":[]}", "not valid JSON");
        assertInvalid("{\"title\":\"t\",\"steps\":[]}", "at least 1 items");
        assertInvalid("{\"title\":\" \",\"steps\":[{\"action\":\"TAP\",\"target\":\"x\",\"expected\":\"y\"}]}", "title is required");
        assertInvalid("{\"title\":\"t\",\"steps\":[{\"action\":\"PRESS_KEY\",\"target\":\"home; reboot\",\"expected\":\"y\"}]}", "uppercase key name");
        assertInvalid("{\"title\":\"t\",\"steps\":[{\"action\":\"TAP\",\"target\":\"x\\u0000\",\"expected\":\"y\"}]}", "control characters");
        assertInvalid("null", "JSON object");
        assertInvalid("", "empty");
        assertInvalid("x".repeat(StructuredChatService.MAX_OUTPUT_CHARS + 1), "exceeded");
    }

    private void assertInvalid(String content, String expectedError) {
        StructuredChatService.Parsed<TestPlanDraft> parsed =
                service.parseAndValidate(content, schema, TestPlanDraft.class, TestPlanValidator::validate);
        assertNull(parsed.value(), content);
        assertTrue(parsed.errors().stream().anyMatch(e -> e.contains(expectedError)), parsed.errors() + " for " + content);
    }

    @Test
    void extractJsonHandlesFencesAndSurroundingProse() {
        assertEquals("{\"a\":1}", StructuredChatService.extractJson("```json\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1}", StructuredChatService.extractJson("```\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1}", StructuredChatService.extractJson("Here you go: {\"a\":1} hope it helps"));
        assertEquals("{\"a\":1}", StructuredChatService.extractJson("  {\"a\":1}  "));
        assertNull(StructuredChatService.extractJson("[1,2,3]"));
        assertNull(StructuredChatService.extractJson("no json here"));
    }

    @Test
    void providerErrorsPropagateWithoutRetryingTheRepairLoop() {
        when(client.chat(anyList(), any(), anyMap()))
                .thenThrow(new AiProviderException(AiProviderException.Reason.TIMEOUT, "timed out"));
        AiProviderException ex = assertThrows(AiProviderException.class, this::draft);
        assertEquals(AiProviderException.Reason.TIMEOUT, ex.getReason());
        verify(client, times(1)).chat(anyList(), any(), anyMap());
    }

    @Test
    void validatorLimits() {
        List<TestPlanDraft.Step> tooMany = java.util.Collections.nCopies(31,
                new TestPlanDraft.Step(TestPlanDraft.Action.WAIT, "1s", "ok"));
        assertTrue(TestPlanValidator.validate(new TestPlanDraft("t", tooMany)).stream().anyMatch(e -> e.contains("at most 30")));
        assertTrue(TestPlanValidator.validate(new TestPlanDraft("t", List.of(
                new TestPlanDraft.Step(TestPlanDraft.Action.PRESS_KEY, "DPAD_DOWN", "focus moves")))).isEmpty());
    }
}


