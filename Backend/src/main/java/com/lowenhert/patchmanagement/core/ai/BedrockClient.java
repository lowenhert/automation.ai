package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.*;

/**
 * Amazon Bedrock Mantle client using the OpenAI-compatible chat completions API.
 */
@Slf4j
@Component
public class BedrockClient {

    private static final long RETRY_BACKOFF_MS = 750;

    private final BedrockProperties properties;
    private final RestClient restClient;
    private final String chatUrl;

    public BedrockClient(BedrockProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getRequestTimeout());

        this.chatUrl = properties.getBaseUrl().toString() + "/chat/completions";

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (properties.hasApiKey()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        }

        this.restClient = builder.build();
        log.info("BedrockClient initialized: chatUrl={} model={}", this.chatUrl, properties.getModel());
    }

    public String model() {
        return properties.getModel();
    }

    public ChatResponse chat(List<ChatMessage> messages, Object format, Map<String, Object> options) {
        return executeWithRetry("chat.completions", () -> doChat(messages, options), properties.getMaxAttempts());
    }

    private ChatResponse doChat(List<ChatMessage> messages, Map<String, Object> options) {
        long started = System.nanoTime();

        List<Map<String, Object>> openAiMessages = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.role());
            if (msg.images() != null && !msg.images().isEmpty()) {
                List<Map<String, Object>> parts = new ArrayList<>();
                if (msg.content() != null && !msg.content().isBlank()) {
                    parts.add(Map.of("type", "text", "text", msg.content()));
                }
                for (String b64 : msg.images()) {
                    parts.add(Map.of("type", "image_url", "image_url",
                            Map.of("url", "data:image/png;base64," + b64)));
                }
                m.put("content", parts);
            } else {
                m.put("content", msg.content() != null ? msg.content() : "");
            }
            openAiMessages.add(m);
        }

        double temperature = 0.0;
        if (options != null && options.get("temperature") instanceof Number n) {
            temperature = n.doubleValue();
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", properties.getModel());
        requestBody.put("messages", openAiMessages);
        requestBody.put("temperature", temperature);

        try {
            JsonNode respNode = restClient.post()
                    .uri(chatUrl)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);

            long elapsedMs = (System.nanoTime() - started) / 1_000_000;

            String text = null;
            String finishReason = "stop";
            if (respNode != null) {
                JsonNode firstChoice = respNode.path("choices").path(0);
                if (!firstChoice.isMissingNode()) {
                    text = firstChoice.path("message").path("content").asText(null);
                    finishReason = firstChoice.path("finish_reason").asText("stop");
                }
            }

            Integer promptTokens = respNode != null ? respNode.path("usage").path("prompt_tokens").asInt(0) : 0;
            Integer completionTokens = respNode != null ? respNode.path("usage").path("completion_tokens").asInt(0) : 0;

            log.info("Bedrock model={} durationMs={} promptTokens={} completionTokens={} chars={}",
                    properties.getModel(), elapsedMs, promptTokens, completionTokens,
                    text != null ? text.length() : 0);

            if (text == null) {
                throw new AiProviderException(AiProviderException.Reason.INVALID_RESPONSE,
                        "Bedrock returned an empty response");
            }

            return new ChatResponse(new ChatMessage("assistant", text), promptTokens, completionTokens, finishReason);
        } catch (HttpStatusCodeException http) {
            String body = http.getResponseBodyAsString();
            log.error("Bedrock API HTTP Error {}: {}", http.getStatusCode(), body);
            throw new AiProviderException(AiProviderException.Reason.PROVIDER_ERROR,
                    "Bedrock returned HTTP " + http.getStatusCode().value() + ": " + body, http);
        }
    }

    public String version() {
        return "bedrock-mantle-openai-v1";
    }

    public String probeModel() {
        ChatResponse resp = executeWithRetry("probe", () -> doChat(
                List.of(ChatMessage.user("Reply with the single word OK.")),
                Map.of("temperature", 0)), 1);
        return resp.message().content();
    }

    private <T> T executeWithRetry(String operation, java.util.function.Supplier<T> call, int maxAttempts) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (AiProviderException e) {
                if (attempt >= maxAttempts) throw e;
                log.warn("Bedrock {} transient error (attempt {}/{}): {}", operation, attempt, maxAttempts, e.getMessage());
                sleep(RETRY_BACKOFF_MS * attempt);
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    throw new AiProviderException(AiProviderException.Reason.PROVIDER_ERROR,
                            "Bedrock " + operation + " failed: " + e.getMessage(), e);
                }
                log.warn("Bedrock {} attempt {} failed: {}", operation, attempt, e.getMessage());
                sleep(RETRY_BACKOFF_MS * attempt);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ChatMessage(String role, String content, List<String> images) {
        public ChatMessage(String role, String content) { this(role, content, null); }
        public static ChatMessage system(String content)  { return new ChatMessage("system", content); }
        public static ChatMessage user(String content)    { return new ChatMessage("user", content); }
        public static ChatMessage assistant(String content) { return new ChatMessage("assistant", content); }
        public static ChatMessage userWithImages(String content, List<String> base64Images) {
            return new ChatMessage("user", content, base64Images);
        }
    }

    public record ChatResponse(ChatMessage message, Integer inputTokens, Integer outputTokens, String stopReason) {
        public Integer promptEvalCount() { return inputTokens; }
        public Integer evalCount()       { return outputTokens; }
        public String  doneReason()      { return stopReason; }
    }
}
