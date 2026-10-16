package com.lowenhert.patchmanagement.core.screenlearning;

import com.lowenhert.patchmanagement.core.auth.JwtTokenUtil;
import com.lowenhert.patchmanagement.core.screenlearning.history.CapturedScreenRepository;
import com.lowenhert.patchmanagement.core.screenlearning.process.ProcessRunner;
import com.lowenhert.patchmanagement.core.user.User;
import com.lowenhert.patchmanagement.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-level tests through the real security chain, controllers, services and JPA (in-memory H2).
 * Only the OS process layer (adb / python) is mocked.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sl-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ScreenLearningApiTest {

    private static final String DEVICES = """
            List of devices attached
            emulator-5554          device product:sdk model:sdk_gphone64 device:emu transport_id:1
            192.168.1.20:5555      offline transport_id:2
            """;

    @TempDir
    static Path captureDir;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.screen-learning.capture.output-dir", () -> captureDir.toString());
        registry.add("app.adb.path", () -> "adb");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CapturedScreenRepository screenRepository;
    @MockBean
    private ProcessRunner processRunner;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        screenRepository.deleteAll();
        if (userRepository.findByUsername("sl-tester").isEmpty()) {
            User user = new User();
            user.setUsername("sl-tester");
            user.setPassword("not-used");
            user.setRole("USER");
            userRepository.save(user);
        }
        token = "Bearer " + jwtTokenUtil.generateToken("sl-tester");

        reset(processRunner);
        when(processRunner.run(command(c -> c.equals(List.of("adb", "devices", "-l"))), any(), anyLong()))
                .thenReturn(new ProcessRunner.ProcessResult(0, DEVICES, ""));
        when(processRunner.run(command(c -> c.contains("getprop")), any(), anyLong()))
                .thenReturn(new ProcessRunner.ProcessResult(0, """
                        [ro.product.model]: [sdk_gphone64]
                        [ro.product.manufacturer]: [Google]
                        [ro.build.version.release]: [15]
                        [ro.build.version.sdk]: [35]
                        """, ""));
        when(processRunner.run(command(c -> c.contains("wm")), any(), anyLong()))
                .thenReturn(new ProcessRunner.ProcessResult(0, "Physical size: 1080x2340", ""));
    }

    /** Null-safe: Mockito re-evaluates existing matchers with null arguments while stubbing. */
    private static List<String> command(Predicate<List<String>> predicate) {
        return argThat(c -> c != null && predicate.test(c));
    }

    private static List<String> python() {
        return command(c -> c.get(0).contains("python"));
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", token);
    }

    // ── Security ────────────────────────────────────────────────────────────

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/screens")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
        mvc.perform(get("/api/screen-learning/adb/devices")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/screens/capture").contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"emulator-5554\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/screens").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
        verifyNoInteractions(processRunner);
    }

    // ── ADB ─────────────────────────────────────────────────────────────────

    @Test
    void listsDevicesWithDetailsForOnlineDevicesOnly() throws Exception {
        mvc.perform(auth(get("/api/screen-learning/adb/devices")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].serial").value("emulator-5554"))
                .andExpect(jsonPath("$[0].connectionType").value("EMULATOR"))
                .andExpect(jsonPath("$[0].androidVersion").value("15"))
                .andExpect(jsonPath("$[0].resolution").value("1080x2340"))
                .andExpect(jsonPath("$[1].state").value("offline"))
                .andExpect(jsonPath("$[1].androidVersion").doesNotExist());
    }

    @Test
    void invalidIpIsRejectedBeforeRunningAdb() throws Exception {
        for (String body : List.of("{\"host\":\"999.1.1.1\"}", "{\"host\":\"1.2.3.4;reboot\"}", "{\"host\":\"\"}",
                "{\"host\":\"192.168.1.20\",\"port\":70000}")) {
            mvc.perform(auth(post("/api/screen-learning/adb/connect")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        verify(processRunner, never()).run(command(c -> c.contains("connect")), any(), anyLong());
    }

    @Test
    void unreachableIpReportsAdbFailure() throws Exception {
        when(processRunner.run(eq(List.of("adb", "connect", "192.168.1.99:5555")), any(), anyLong()))
                .thenReturn(new ProcessRunner.ProcessResult(0, "failed to connect to '192.168.1.99:5555': Connection timed out", ""));
        mvc.perform(auth(post("/api/screen-learning/adb/connect")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"192.168.1.99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Connection timed out")));
    }

    @Test
    void connectAndDisconnectNetworkDevice() throws Exception {
        when(processRunner.run(eq(List.of("adb", "connect", "192.168.1.20:5555")), any(), anyLong()))
                .thenReturn(new ProcessRunner.ProcessResult(0, "connected to 192.168.1.20:5555", ""));
        when(processRunner.run(eq(List.of("adb", "disconnect", "192.168.1.20:5555")), any(), anyLong()))
                .thenReturn(new ProcessRunner.ProcessResult(0, "disconnected 192.168.1.20:5555", ""));

        mvc.perform(auth(post("/api/screen-learning/adb/connect")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"host\":\"192.168.1.20\",\"port\":5555}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.serial").value("192.168.1.20:5555"));
        mvc.perform(auth(post("/api/screen-learning/adb/disconnect")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serial\":\"192.168.1.20:5555\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(auth(post("/api/screen-learning/adb/disconnect")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serial\":\"emulator-5554\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adbTimeoutReturns503() throws Exception {
        when(processRunner.run(eq(List.of("adb", "devices", "-l")), any(), anyLong())).thenThrow(new TimeoutException());
        mvc.perform(auth(get("/api/screen-learning/adb/devices")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("ADB command timed out"));
    }

    // ── Capture ─────────────────────────────────────────────────────────────

    @Test
    void offlineAndUnknownDevicesAreRejectedWithoutRunningPython() throws Exception {
        mvc.perform(auth(post("/api/screens/capture")).contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"192.168.1.20:5555\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("offline")));
        mvc.perform(auth(post("/api/screens/capture")).contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"no-such-device\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/screens/capture")).contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"-s\"}"))
                .andExpect(status().isBadRequest());
        verify(processRunner, never()).run(python(), any(), anyLong());
    }

    @Test
    void failedCaptureReturns503AndStoresNothing() throws Exception {
        when(processRunner.run(python(), any(), anyLong()))
                .thenReturn(new ProcessRunner.ProcessResult(6,
                        "{\"status\":\"FAILED\",\"errorCode\":\"INVALID_PNG\",\"message\":\"Device did not return a valid PNG image\"}", ""));
        mvc.perform(auth(post("/api/screens/capture")).contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"emulator-5554\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Device did not return a valid PNG image"));
        org.junit.jupiter.api.Assertions.assertEquals(0, screenRepository.count());
    }

    @Test
    void captureTimeoutReturns503() throws Exception {
        when(processRunner.run(python(), any(), anyLong())).thenThrow(new TimeoutException());
        mvc.perform(auth(post("/api/screens/capture")).contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"emulator-5554\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Screen capture timed out"));
    }

    // ── Capture → history → preview/search ──────────────────────────────────

    @Test
    void capturedScreenIsPersistedAndServedThroughHistoryApis() throws Exception {
        when(processRunner.run(python(), any(), anyLong()))
                .thenAnswer(inv -> fakeSuccessfulCapture(inv.getArgument(0)));

        String body = mvc.perform(auth(post("/api/screens/capture")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"emulator-5554\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.textRegionCount").value(2))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.captureId");

        mvc.perform(auth(get("/api/screens")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].deviceModel").value("sdk gphone64"))
                .andExpect(jsonPath("$.content[0].resolution").value("1080x2340"))
                .andExpect(jsonPath("$.content[0].phash").value("c5ffd59b90a02ae0"));

        mvc.perform(auth(get("/api/screens/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capturedAt").value("2026-10-15T07:00:00Z"))
                .andExpect(jsonPath("$.textRegions", hasSize(2)))
                .andExpect(jsonPath("$.textRegions[0].type").value("OCR_TEXT"))
                .andExpect(jsonPath("$.textRegions[0].text").value("Network internet"))
                .andExpect(jsonPath("$.textRegions[0].bbox.x").value(224))
                .andExpect(jsonPath("$.note", containsString("not verified")));

        mvc.perform(auth(get("/api/screens/" + id + "/image")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[]{(byte) 0x89, 'P', 'N', 'G'}));

        mvc.perform(auth(get("/api/screens/search").param("text", "BLUETOOTH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].matchedTexts[0]").value("Bluetooth, pairing"));
        mvc.perform(auth(get("/api/screens/search").param("text", "100%")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void historyValidationAndNotFound() throws Exception {
        mvc.perform(auth(get("/api/screens").param("size", "500"))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/screens").param("page", "-1"))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/screens").param("page", "abc"))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/screens/search"))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("text")));
        mvc.perform(auth(get("/api/screens/search").param("text", "a"))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/screens/not-a-uuid"))).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/screens/" + UUID.randomUUID()))).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/screens/" + UUID.randomUUID() + "/image"))).andExpect(status().isNotFound());
    }

    private ProcessRunner.ProcessResult fakeSuccessfulCapture(List<String> cmd) throws Exception {
        String id = cmd.get(cmd.indexOf("--capture-id") + 1);
        Path dir = Path.of(cmd.get(cmd.indexOf("--output-dir") + 1));
        Files.write(dir.resolve(id + ".png"), new byte[]{(byte) 0x89, 'P', 'N', 'G'});
        Files.writeString(dir.resolve(id + ".json"), """
                {"checksums":{"sha256":"%s"},
                 "perceptualHashes":{"phash":"c5ffd59b90a02ae0","dhash":"8a000338c01396f2","ahash":"fffee00000007a7e"},
                 "ocr":{"status":"SUCCESS","textRegions":[
                   {"id":1,"type":"OCR_TEXT","text":"Network internet","confidence":94.5,"bbox":{"x":224,"y":720,"width":447,"height":42}},
                   {"id":2,"type":"OCR_TEXT","text":"Bluetooth, pairing","confidence":95.5,"bbox":{"x":223,"y":1004,"width":298,"height":38}}]}}
                """.formatted("b".repeat(64)));
        return new ProcessRunner.ProcessResult(0, ("{\"status\":\"SUCCESS\",\"captureId\":\"%s\",\"deviceId\":\"emulator-5554\","
                + "\"fileName\":\"%s.png\",\"width\":1080,\"height\":2340,\"resolution\":\"1080x2340\",\"sizeBytes\":4,"
                + "\"timestamp\":\"2026-10-15T07:00:00Z\",\"analysis\":{\"status\":\"SUCCESS\",\"ocrStatus\":\"SUCCESS\","
                + "\"textRegionCount\":2,\"ocrError\":null}}").formatted(id, id), "");
    }
}
