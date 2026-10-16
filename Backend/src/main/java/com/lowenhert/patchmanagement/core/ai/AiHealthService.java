package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Verifies Ollama connectivity, model availability and (optionally) that the model can generate. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiHealthService {

    private final OllamaClient client;
    private final OllamaProperties properties;

    public AiHealth check(boolean probeGeneration) {
        long started = System.nanoTime();
        AiHealth.AiHealthBuilder health = AiHealth.builder()
                .provider("ollama")
                .baseUrl(properties.getBaseUrl().toString())
                .model(properties.getModel());

        try {
            health.ollamaVersion(client.version()).reachable(true);
        } catch (AiProviderException e) {
            return down(health, started, e);
        }

        try {
            JsonNode show = client.showModel(properties.getModel());
            List<String> capabilities = new ArrayList<>();
            show.path("capabilities").forEach(c -> capabilities.add(c.asText()));
            health.modelAvailable(true).capabilities(capabilities);
        } catch (AiProviderException e) {
            health.modelAvailable(false);
            return down(health, started, e);
        }

        if (probeGeneration) {
            health.generationChecked(true);
            try {
                String reply = client.chat(List.of(OllamaClient.ChatMessage.user("Reply with the single word OK.")),
                        null, Map.of("temperature", 0, "num_predict", 8)).message().content();
                health.generationOk(reply != null && !reply.isBlank());
            } catch (AiProviderException e) {
                health.generationOk(false);
                return down(health, started, e);
            }
        }

        return health.status("UP").latencyMs(elapsedMs(started)).build();
    }

    private AiHealth down(AiHealth.AiHealthBuilder health, long started, AiProviderException e) {
        log.warn("AI health check failed: {} - {}", e.getReason(), e.getMessage());
        return health.status("DOWN")
                .latencyMs(elapsedMs(started))
                .error(new AiHealth.Error(e.getReason().name(), e.getMessage()))
                .build();
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AiHealth(String status, String provider, String baseUrl, String model, String ollamaVersion,
                           boolean reachable, boolean modelAvailable, List<String> capabilities,
                           Boolean generationChecked, Boolean generationOk, long latencyMs, Error error) {

        public boolean isUp() {
            return "UP".equals(status);
        }

        public record Error(String reason, String message) {
        }
    }
}
