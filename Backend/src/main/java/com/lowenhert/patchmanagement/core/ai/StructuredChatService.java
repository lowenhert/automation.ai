package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Requests JSON from the LLM and returns it only after strict parsing, JSON Schema validation and
 * application-level validation. Raw model output is never returned to callers.
 */
@Slf4j
@Service
public class StructuredChatService {

    static final int MAX_OUTPUT_CHARS = 100_000;
    static final int DEFAULT_MAX_ATTEMPTS = 2;
    public static final int MAX_ATTEMPTS_LIMIT = 3;
    private static final Pattern FENCED = Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Map<String, Object> DETERMINISTIC = Map.of("temperature", 0);

    private final OllamaClient client;
    private final JsonSchemaValidator schemaValidator;
    // Strict: unknown fields, trailing tokens, duplicate keys or wrong types make the output invalid
    private final ObjectMapper strictMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .build();

    public StructuredChatService(OllamaClient client, JsonSchemaValidator schemaValidator) {
        this.client = client;
        this.schemaValidator = schemaValidator;
    }

    /** A validated value plus how many model calls it took. */
    public record Result<T>(T value, int attempts) {
    }

    /**
     * @param schema    JSON Schema sent as Ollama {@code format}, included in the system prompt
     *                  (some models, including cloud models, ignore {@code format}) and enforced on the response
     * @param validator returns application-level errors; empty means valid
     */
    public <T> T chatJson(String systemPrompt, String userPrompt, JsonNode schema, Class<T> type,
                          Function<T, List<String>> validator) {
        return chatJson(systemPrompt, userPrompt, List.of(), schema, type, validator);
    }

    /** Same as above, attaching base64 images to the user message. Callers must verify the model actually used them. */
    public <T> T chatJson(String systemPrompt, String userPrompt, List<String> images, JsonNode schema, Class<T> type,
                          Function<T, List<String>> validator) {
        return chatJsonDetailed(systemPrompt, userPrompt, images, schema, type, validator, DEFAULT_MAX_ATTEMPTS).value();
    }

    public <T> Result<T> chatJsonDetailed(String systemPrompt, String userPrompt, List<String> images, JsonNode schema,
                                          Class<T> type, Function<T, List<String>> validator, int maxAttempts) {
        if (maxAttempts < 1 || maxAttempts > MAX_ATTEMPTS_LIMIT) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and " + MAX_ATTEMPTS_LIMIT);
        }
        String system = systemPrompt + "\n\nRespond with a single JSON object only, no Markdown, no explanation. "
                + "It must validate against this JSON Schema:\n" + schema.toString();
        List<OllamaClient.ChatMessage> messages = new ArrayList<>(List.of(
                OllamaClient.ChatMessage.system(system),
                images.isEmpty() ? OllamaClient.ChatMessage.user(userPrompt)
                        : OllamaClient.ChatMessage.userWithImages(userPrompt, images)));

        List<String> errors = List.of();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String content = client.chat(List.copyOf(messages), schema, DETERMINISTIC).message().content();
            Parsed<T> parsed = parseAndValidate(content, schema, type, validator);
            if (parsed.value() != null) {
                if (attempt > 1) log.info("LLM output valid after {} attempts", attempt);
                return new Result<>(parsed.value(), attempt);
            }
            errors = parsed.errors();
            log.warn("LLM output rejected (attempt {}/{}): {}", attempt, maxAttempts, errors);
            // Repair round: show the model its own output and the exact problems
            messages.add(OllamaClient.ChatMessage.assistant(truncate(content, 4000)));
            messages.add(OllamaClient.ChatMessage.user("Your previous response was invalid: " + String.join("; ", errors)
                    + ". Respond again with only the corrected JSON object."));
        }
        throw new AiProviderException(AiProviderException.Reason.INVALID_OUTPUT,
                "Model output failed validation after " + maxAttempts + " attempt(s): " + String.join("; ", errors));
    }

    <T> Parsed<T> parseAndValidate(String content, JsonNode schema, Class<T> type, Function<T, List<String>> validator) {
        if (content == null || content.isBlank()) return Parsed.invalid("response was empty");
        if (content.length() > MAX_OUTPUT_CHARS) return Parsed.invalid("response exceeded " + MAX_OUTPUT_CHARS + " characters");

        String json = extractJson(content);
        if (json == null) return Parsed.invalid("response did not contain a JSON object");

        JsonNode tree;
        try {
            tree = strictMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return Parsed.invalid("response is not valid JSON: " + summarize(e));
        }
        if (tree == null || !tree.isObject()) return Parsed.invalid("response did not contain a JSON object");

        List<String> schemaErrors = schemaValidator.validate(schema, tree);
        if (!schemaErrors.isEmpty()) {
            return Parsed.invalid(schemaErrors.stream().map(e -> "schema violation: " + e).toList());
        }

        T value;
        try {
            value = strictMapper.treeToValue(tree, type);
        } catch (JsonProcessingException e) {
            return Parsed.invalid("JSON does not match the expected structure: " + summarize(e));
        }

        List<String> errors = validator.apply(value);
        return errors == null || errors.isEmpty() ? new Parsed<>(value, List.of()) : Parsed.invalid(errors);
    }

    /** Accepts a bare JSON object, or one wrapped in a Markdown code fence. Top-level arrays are rejected. */
    static String extractJson(String content) {
        String text = content.strip();
        Matcher fenced = FENCED.matcher(text);
        if (fenced.find()) text = fenced.group(1).strip();
        if (text.startsWith("[")) return null;
        if (text.startsWith("{") && text.endsWith("}")) return text;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : null;
    }

    private static String summarize(JsonProcessingException e) {
        String msg = e.getOriginalMessage();
        if (msg == null) msg = e.getClass().getSimpleName();
        int nl = msg.indexOf('\n');
        return truncate(nl > 0 ? msg.substring(0, nl) : msg, 200);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    record Parsed<T>(T value, List<String> errors) {
        static <T> Parsed<T> invalid(String error) {
            return new Parsed<>(null, List.of(error));
        }

        static <T> Parsed<T> invalid(List<String> errors) {
            return new Parsed<>(null, errors);
        }
    }
}
