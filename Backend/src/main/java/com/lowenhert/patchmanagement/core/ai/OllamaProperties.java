package com.lowenhert.patchmanagement.core.ai;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/** Ollama settings, all sourced from environment variables (see application.properties). */
@Slf4j
@Getter
@Component
public class OllamaProperties {

    private final URI baseUrl;
    private final String model;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final String apiKey;
    private final int maxAttempts;

    public OllamaProperties(@Value("${app.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
                            @Value("${app.ai.ollama.model:gemma4:31b-cloud}") String model,
                            @Value("${app.ai.ollama.connect-timeout-ms:5000}") long connectTimeoutMs,
                            @Value("${app.ai.ollama.request-timeout-ms:120000}") long requestTimeoutMs,
                            @Value("${app.ai.ollama.api-key:}") String apiKey,
                            @Value("${app.ai.ollama.max-attempts:2}") int maxAttempts) {
        this.baseUrl = parseBaseUrl(baseUrl);
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("app.ai.ollama.model (OLLAMA_MODEL) must not be blank");
        }
        if (connectTimeoutMs <= 0 || requestTimeoutMs <= 0) {
            throw new IllegalStateException("Ollama timeouts must be positive");
        }
        if (maxAttempts < 1 || maxAttempts > 5) {
            throw new IllegalStateException("app.ai.ollama.max-attempts must be between 1 and 5");
        }
        this.model = model.trim();
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs);
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.maxAttempts = maxAttempts;
        // Never log the API key
        log.info("Ollama configured: baseUrl={} model={} requestTimeout={}s apiKey={}",
                this.baseUrl, this.model, this.requestTimeout.toSeconds(), this.apiKey.isEmpty() ? "none" : "set");
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    private static URI parseBaseUrl(String value) {
        try {
            URI uri = URI.create(value.trim().replaceAll("/+$", ""));
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalStateException("Do not embed credentials in OLLAMA_BASE_URL; use OLLAMA_API_KEY");
            }
            return uri;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("Invalid OLLAMA_BASE_URL: must be an http(s) URL, e.g. http://localhost:11434");
        }
    }
}
