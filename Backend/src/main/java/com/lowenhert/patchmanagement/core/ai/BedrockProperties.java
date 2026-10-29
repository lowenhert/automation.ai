package com.lowenhert.patchmanagement.core.ai;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;

/**
 * Amazon Bedrock Mantle (OpenAI-compatible) configuration.
 */
@Slf4j
@Getter
@Component
public class BedrockProperties {

    private final URI baseUrl;
    private final String model;
    private final String apiKey;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final int maxAttempts;

    public BedrockProperties(
            @Value("${app.ai.bedrock.base-url:https://bedrock-mantle.us-east-1.api.aws/v1}") String baseUrl,
            @Value("${app.ai.bedrock.model:deepseek.v3.2}") String model,
            @Value("${app.ai.bedrock.api-key:}") String apiKey,
            @Value("${app.ai.bedrock.connect-timeout-ms:10000}") long connectTimeoutMs,
            @Value("${app.ai.bedrock.request-timeout-ms:120000}") long requestTimeoutMs,
            @Value("${app.ai.bedrock.max-attempts:2}") int maxAttempts) {

        this.baseUrl = parseBaseUrl(baseUrl);
        this.model = (model == null || model.isBlank()) ? "deepseek.v3.2" : model.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.connectTimeout = Duration.ofMillis(connectTimeoutMs <= 0 ? 10000 : connectTimeoutMs);
        this.requestTimeout = Duration.ofMillis(requestTimeoutMs <= 0 ? 120000 : requestTimeoutMs);
        this.maxAttempts = (maxAttempts < 1 || maxAttempts > 5) ? 2 : maxAttempts;

        log.info("Bedrock configured: baseUrl={} model={} hasApiKey={}",
                this.baseUrl, this.model, !this.apiKey.isEmpty());
    }

    public boolean hasApiKey() {
        return !apiKey.isEmpty();
    }

    private static URI parseBaseUrl(String value) {
        String clean = value == null ? "https://bedrock-mantle.us-east-1.api.aws/v1" : value.trim().replaceAll("/+$", "");
        return URI.create(clean);
    }
}
