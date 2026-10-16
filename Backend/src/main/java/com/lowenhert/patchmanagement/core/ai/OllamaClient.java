package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/** Minimal Ollama REST client (non-streaming). See https://github.com/ollama/ollama/blob/main/docs/api.md */
@Slf4j
@Component
public class OllamaClient {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private static final long RETRY_BACKOFF_MS = 750;

    private final OllamaProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OllamaClient(OllamaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getRequestTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (properties.hasApiKey()) {
            boolean secure = "https".equals(properties.getBaseUrl().getScheme())
                    || LOCAL_HOSTS.contains(properties.getBaseUrl().getHost());
            if (!secure) {
                throw new IllegalStateException("OLLAMA_API_KEY must only be sent over https (or to localhost)");
            }
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey());
        }
        this.restClient = builder.build();
    }

    public String model() {
        return properties.getModel();
    }

    /** GET /api/version */
    public String version() {
        JsonNode body = execute("GET /api/version", () ->
                restClient.get().uri("/api/version").retrieve().body(JsonNode.class), 1);
        return body == null ? null : body.path("version").asText(null);
    }

    /** POST /api/show — works for local and cloud models (cloud models are not listed by /api/tags). */
    public JsonNode showModel(String model) {
        return execute("POST /api/show", () -> restClient.post().uri("/api/show")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model))
                .retrieve().body(JsonNode.class), 1);
    }

    /** POST /api/chat with stream=false. */
    public ChatResponse chat(List<ChatMessage> messages, Object format, Map<String, Object> options) {
        ChatRequest request = new ChatRequest(properties.getModel(), messages, false, false, format, options);
        long started = System.nanoTime();
        ChatResponse response = execute("POST /api/chat", () -> restClient.post().uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve().body(ChatResponse.class), properties.getMaxAttempts());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        if (response == null || response.message() == null || response.message().content() == null) {
            throw new AiProviderException(AiProviderException.Reason.INVALID_RESPONSE, "Ollama returned an empty chat response");
        }
        log.info("Ollama chat model={} durationMs={} promptTokens={} outputTokens={} doneReason={} chars={}",
                properties.getModel(), elapsedMs, response.promptEvalCount(), response.evalCount(),
                response.doneReason(), response.message().content().length());
        if (log.isDebugEnabled()) {
            log.debug("Ollama chat output (truncated): {}", truncate(response.message().content(), 500));
        }
        return response;
    }

    private <T> T execute(String operation, java.util.function.Supplier<T> call, int maxAttempts) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (RestClientException e) {
                AiProviderException mapped = map(operation, e);
                boolean retryable = mapped.getReason() == AiProviderException.Reason.PROVIDER_ERROR && isTransient(e);
                if (!retryable || attempt >= maxAttempts) {
                    log.warn("Ollama {} failed (attempt {}/{}): {} - {}", operation, attempt, maxAttempts,
                            mapped.getReason(), mapped.getMessage());
                    throw mapped;
                }
                log.warn("Ollama {} transient failure (attempt {}/{}): {}; retrying", operation, attempt, maxAttempts, mapped.getMessage());
                sleep(RETRY_BACKOFF_MS * attempt);
            }
        }
    }

    AiProviderException map(String operation, RestClientException e) {
        String target = properties.getBaseUrl() + " (" + operation + ")";
        if (e instanceof HttpStatusCodeException http) {
            String detail = ollamaError(http.getResponseBodyAsString());
            HttpStatusCode status = http.getStatusCode();
            if (status.value() == 404) {
                return new AiProviderException(AiProviderException.Reason.MODEL_NOT_FOUND,
                        "Model '" + properties.getModel() + "' is not available on " + properties.getBaseUrl()
                                + (detail != null ? ": " + detail : ""), e);
            }
            if (status.value() == 401 || status.value() == 403) {
                return new AiProviderException(AiProviderException.Reason.UNAUTHORIZED,
                        "Ollama rejected the request (" + status.value() + "). For cloud models run 'ollama signin' or set OLLAMA_API_KEY"
                                + (detail != null ? ": " + detail : ""), e);
            }
            return new AiProviderException(AiProviderException.Reason.PROVIDER_ERROR,
                    "Ollama returned HTTP " + status.value() + (detail != null ? ": " + detail : ""), e);
        }
        Throwable root = rootCause(e);
        // The JDK client wraps causes inconsistently (e.g. ConnectException -> ClosedChannelException), so scan the chain
        if (hasCause(e, HttpConnectTimeoutException.class, ConnectException.class, UnknownHostException.class)) {
            return new AiProviderException(AiProviderException.Reason.CONNECTION_FAILED,
                    "Cannot connect to Ollama at " + target + ". Is Ollama running?", e);
        }
        // Spring's JdkClientHttpRequestFactory reports read timeouts as IOException(TimeoutException)
        if (hasCause(e, HttpTimeoutException.class, SocketTimeoutException.class, TimeoutException.class)) {
            return new AiProviderException(AiProviderException.Reason.TIMEOUT,
                    "Ollama did not respond within " + properties.getRequestTimeout().toSeconds() + "s (" + operation + ")", e);
        }
        if (e instanceof ResourceAccessException) {
            return new AiProviderException(AiProviderException.Reason.CONNECTION_FAILED,
                    "I/O error talking to Ollama at " + target + ": " + root.getMessage(), e);
        }
        return new AiProviderException(AiProviderException.Reason.INVALID_RESPONSE,
                "Unexpected response from Ollama (" + operation + "): " + root.getMessage(), e);
    }

    private static boolean isTransient(RestClientException e) {
        if (e instanceof HttpStatusCodeException http) {
            int s = http.getStatusCode().value();
            return s == 429 || s == 502 || s == 503 || s == 504;
        }
        return false;
    }

    private String ollamaError(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            String error = objectMapper.readTree(body).path("error").asText(null);
            return error != null ? truncate(error, 300) : truncate(body, 300);
        } catch (Exception ignored) {
            return truncate(body, 300);
        }
    }

    @SafeVarargs
    private static boolean hasCause(Throwable t, Class<? extends Throwable>... types) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            for (Class<? extends Throwable> type : types) {
                if (type.isInstance(c)) return true;
            }
        }
        return false;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AiProviderException(AiProviderException.Reason.PROVIDER_ERROR, "Interrupted while retrying Ollama request");
        }
    }

    // ── Wire types ──────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ChatMessage(String role, String content, List<String> images) {
        public ChatMessage(String role, String content) { this(role, content, null); }

        public static ChatMessage system(String content) { return new ChatMessage("system", content); }
        public static ChatMessage user(String content) { return new ChatMessage("user", content); }
        /** User message with base64-encoded images (Ollama's image input format). */
        public static ChatMessage userWithImages(String content, List<String> base64Images) {
            return new ChatMessage("user", content, base64Images);
        }
        public static ChatMessage assistant(String content) { return new ChatMessage("assistant", content); }

        @Override
        public String toString() {
            // Keep base64 image data out of logs and exception messages
            return "ChatMessage[role=" + role + ", chars=" + (content == null ? 0 : content.length())
                    + ", images=" + (images == null ? 0 : images.size()) + "]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChatRequest(String model, List<ChatMessage> messages, boolean stream, Boolean think,
                       Object format, Map<String, Object> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(String model, ChatMessage message, boolean done,
                               @com.fasterxml.jackson.annotation.JsonProperty("done_reason") String doneReason,
                               @com.fasterxml.jackson.annotation.JsonProperty("prompt_eval_count") Integer promptEvalCount,
                               @com.fasterxml.jackson.annotation.JsonProperty("eval_count") Integer evalCount,
                               @com.fasterxml.jackson.annotation.JsonProperty("total_duration") Long totalDuration) {
    }
}
