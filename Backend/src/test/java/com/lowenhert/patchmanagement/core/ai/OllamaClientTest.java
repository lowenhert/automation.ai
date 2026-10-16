package com.lowenhert.patchmanagement.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises OllamaClient over real HTTP against an in-process stub server. */
class OllamaClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void respond(String path, int status, String body) {
        respond(path, status, body, 0);
    }

    private void respond(String path, int status, String body, long delayMs) {
        server.createContext(path, exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

    private OllamaClient client(long requestTimeoutMs, String apiKey, String url) {
        return new OllamaClient(new OllamaProperties(url, "gemma4:31b-cloud", 2000, requestTimeoutMs, apiKey, 2), new ObjectMapper());
    }

    private OllamaClient client() {
        return client(5000, "", baseUrl);
    }

    @Test
    void chatSendsNonStreamingRequestAndParsesResponse() throws Exception {
        respond("/api/chat", 200, """
                {"model":"gemma4:31b-cloud","message":{"role":"assistant","content":"pong","thinking":""},
                 "done":true,"done_reason":"stop","prompt_eval_count":12,"eval_count":2,"total_duration":5000,"extra":1}
                """);

        OllamaClient.ChatResponse response = client().chat(List.of(OllamaClient.ChatMessage.user("ping")),
                Map.of("type", "object"), Map.of("temperature", 0));

        assertEquals("pong", response.message().content());
        assertEquals("stop", response.doneReason());
        assertEquals(2, response.evalCount());

        JsonNode sent = new ObjectMapper().readTree(lastBody.get());
        assertEquals("gemma4:31b-cloud", sent.path("model").asText());
        assertFalse(sent.path("stream").asBoolean(true));
        assertFalse(sent.path("think").asBoolean(true));
        assertEquals("object", sent.path("format").path("type").asText());
        assertEquals("user", sent.path("messages").get(0).path("role").asText());
        assertNull(lastAuth.get(), "no Authorization header without OLLAMA_API_KEY");
    }

    @Test
    void missingModelIsReportedAsModelNotFound() {
        respond("/api/show", 404, "{\"error\":\"model 'gemma4:31b-cloud' not found\"}");
        AiProviderException ex = assertThrows(AiProviderException.class, () -> client().showModel("gemma4:31b-cloud"));
        assertEquals(AiProviderException.Reason.MODEL_NOT_FOUND, ex.getReason());
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void unauthorizedCloudModelExplainsSignin() {
        respond("/api/chat", 401, "{\"error\":\"unauthorized\"}");
        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client().chat(List.of(OllamaClient.ChatMessage.user("hi")), null, null));
        assertEquals(AiProviderException.Reason.UNAUTHORIZED, ex.getReason());
        assertTrue(ex.getMessage().contains("ollama signin"));
    }

    @Test
    void transientServerErrorIsRetriedOnce() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/api/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            boolean first = calls.incrementAndGet() == 1;
            byte[] bytes = (first ? "{\"error\":\"upstream overloaded\"}"
                    : "{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"done\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(first ? 503 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });

        assertEquals("ok", client().chat(List.of(OllamaClient.ChatMessage.user("hi")), null, null).message().content());
        assertEquals(2, calls.get());
    }

    @Test
    void persistentServerErrorFailsAfterMaxAttempts() {
        respond("/api/chat", 500, "{\"error\":\"boom\"}");
        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client().chat(List.of(OllamaClient.ChatMessage.user("hi")), null, null));
        assertEquals(AiProviderException.Reason.PROVIDER_ERROR, ex.getReason());
        assertTrue(ex.getMessage().contains("boom"));
    }

    @Test
    void slowResponseTimesOut() {
        respond("/api/chat", 200, "{\"message\":{\"role\":\"assistant\",\"content\":\"late\"}}", 1500);
        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client(300, "", baseUrl).chat(List.of(OllamaClient.ChatMessage.user("hi")), null, null));
        assertEquals(AiProviderException.Reason.TIMEOUT, ex.getReason(), () -> causeChain(ex));
    }

    private static String causeChain(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            sb.append(" <- ").append(c.getClass().getName()).append(": ").append(c.getMessage());
        }
        return sb.toString();
    }

    @Test
    void refusedConnectionIsReported() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client(2000, "", "http://127.0.0.1:" + closedPort).version());
        assertEquals(AiProviderException.Reason.CONNECTION_FAILED, ex.getReason());
        assertTrue(ex.getMessage().contains("Is Ollama running?"));
    }

    @Test
    void emptyChatResponseIsInvalid() {
        respond("/api/chat", 200, "{\"done\":true}");
        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client().chat(List.of(OllamaClient.ChatMessage.user("hi")), null, null));
        assertEquals(AiProviderException.Reason.INVALID_RESPONSE, ex.getReason());
    }

    @Test
    void apiKeyIsSentAsBearerToLocalhost() {
        respond("/api/version", 200, "{\"version\":\"0.40.4\"}");
        assertEquals("0.40.4", client(2000, "test-key", baseUrl).version());
        assertEquals("Bearer test-key", lastAuth.get());
    }

    @Test
    void apiKeyIsNeverSentOverPlainHttpToRemoteHosts() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client(2000, "test-key", "http://ollama.example.com"));
        assertTrue(ex.getMessage().contains("https"));
    }
}
