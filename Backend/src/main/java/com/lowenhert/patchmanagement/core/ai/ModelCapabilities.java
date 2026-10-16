package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Caches the configured model's capabilities from POST /api/show (e.g. "vision"). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelCapabilities {

    static final Duration TTL = Duration.ofMinutes(10);

    private final OllamaClient client;
    private volatile Set<String> cached;
    private volatile Instant cachedAt = Instant.EPOCH;

    /** True only if Ollama reports the "vision" capability. Unknown (provider error) is treated as false. */
    public boolean supportsVision() {
        return capabilities().contains("vision");
    }

    public synchronized Set<String> capabilities() {
        if (cached != null && Instant.now().isBefore(cachedAt.plus(TTL))) return cached;
        try {
            JsonNode show = client.showModel(client.model());
            Set<String> caps = new HashSet<>();
            show.path("capabilities").forEach(c -> caps.add(c.asText()));
            cached = Set.copyOf(caps);
            cachedAt = Instant.now();
            log.info("Model {} capabilities: {}", client.model(), cached);
            return cached;
        } catch (AiProviderException e) {
            log.warn("Could not read capabilities for {}: {}", client.model(), e.getMessage());
            return Set.of();
        }
    }
}
