package com.lowenhert.patchmanagement.core.ai;

import com.lowenhert.patchmanagement.core.auth.JwtTokenUtil;
import com.lowenhert.patchmanagement.core.user.User;
import com.lowenhert.patchmanagement.core.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP-level behaviour of /api/ai when Ollama is unreachable (port 9 on localhost is the discard port; nothing listens). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.ai.ollama.base-url=http://127.0.0.1:9",
        "app.ai.ollama.connect-timeout-ms=2000"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AiApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    @Autowired
    private UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("ai-tester").isEmpty()) {
            User user = new User();
            user.setUsername("ai-tester");
            user.setPassword("not-used");
            user.setRole("USER");
            userRepository.save(user);
        }
        token = "Bearer " + jwtTokenUtil.generateToken("ai-tester");
    }

    @Test
    void healthRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/ai/health")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/test-plans/draft").contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"open settings\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthReportsDownWhenOllamaIsUnreachable() throws Exception {
        mvc.perform(get("/api/ai/health").header("Authorization", token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.model").value("gemma4:31b-cloud"))
                .andExpect(jsonPath("$.reachable").value(false))
                .andExpect(jsonPath("$.error.reason").value("CONNECTION_FAILED"))
                .andExpect(jsonPath("$.error.message", containsString("Is Ollama running?")));
    }

    @Test
    void draftValidatesInputBeforeCallingTheModel() throws Exception {
        mvc.perform(post("/api/ai/test-plans/draft").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("goal")));
        mvc.perform(post("/api/ai/test-plans/draft").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"open settings\",\"context\":\"" + "x".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void draftReturns503WhenOllamaIsUnreachable() throws Exception {
        mvc.perform(post("/api/ai/test-plans/draft").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"goal\":\"Open Settings and check Wi-Fi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("Cannot connect to Ollama")));
    }
}
