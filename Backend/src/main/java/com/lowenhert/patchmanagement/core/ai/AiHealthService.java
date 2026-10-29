package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Verifies Amazon Bedrock Mantle connectivity and that the configured model is reachable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiHealthService {

    private final BedrockClient client;
    private final BedrockProperties properties;

    public AiHealth check(boolean probeGeneration) {
        long started = System.nanoTime();
        AiHealth.AiHealthBuilder health = AiHealth.builder()
                .provider("amazon-bedrock-mantle")
                .baseUrl(properties.getBaseUrl().toString())
                .model(properties.getModel())
                .providerVersion(client.version())
                .reachable(true);

        if (probeGeneration) {
            health.generationChecked(true);
            try {
                String reply = client.probeModel();
                health.generationOk(reply != null && !reply.isBlank())
                      .modelAvailable(true)
                      .capabilities(List.of("text", "chat"));
            } catch (AiProviderException e) {
                health.generationOk(false).modelAvailable(false);
                return down(health, started, e);
            }
        } else {
            health.modelAvailable(true).capabilities(List.of("text", "chat"));
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
    public record AiHealth(String status, String provider, String baseUrl, String model, String providerVersion,
                           boolean reachable, boolean modelAvailable, List<String> capabilities,
                           Boolean generationChecked, Boolean generationOk, long latencyMs, Error error) {

        public boolean isUp() {
            return "UP".equals(status);
        }

        public record Error(String reason, String message) {
        }
    }
}
