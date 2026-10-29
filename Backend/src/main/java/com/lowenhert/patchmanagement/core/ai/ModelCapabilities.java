package com.lowenhert.patchmanagement.core.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Caches the configured Bedrock model's capabilities.
 * For Bedrock, capabilities are determined by the model ID family rather than a live API call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCapabilities {

    static final Duration TTL = Duration.ofMinutes(10);

    private final BedrockClient client;
    private volatile Set<String> cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    /**
     * True if the model supports vision (image) input.
     * Claude 3+ models, Llama 3.2 vision models, and Amazon Nova Pro support vision.
     */
    public boolean supportsVision() {
        return capabilities().contains("vision");
    }

    public synchronized Set<String> capabilities() {
        if (cached != null && Instant.now().isBefore(cachedAt.plus(TTL))) return cached;
        String modelId = client.model().toLowerCase();

        // Determine capabilities from model ID family — Bedrock has no /show equivalent
        Set<String> caps;
        if (modelId.contains("claude-3") || modelId.contains("claude-3-5") || modelId.contains("claude-3-7")
                || modelId.contains("llama3.2") || modelId.contains("nova-pro") || modelId.contains("nova-lite")) {
            caps = Set.of("text", "vision");
        } else {
            caps = Set.of("text");
        }

        cached = caps;
        cachedAt = Instant.now();
        log.info("Bedrock model {} capabilities: {}", client.model(), cached);
        return cached;
    }
}
