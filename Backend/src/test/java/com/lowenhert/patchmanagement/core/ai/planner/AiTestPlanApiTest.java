package com.lowenhert.patchmanagement.core.ai.planner;

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

/** HTTP contract of /api/ai/test-plans with Ollama unreachable (port 9). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:planner-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.ai.ollama.base-url=http://127.0.0.1:9"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AiTestPlanApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("planner").isEmpty()) {
            User user = new User();
            user.setUsername("planner");
            user.setPassword("not-used");
            user.setRole("USER");
            userRepository.save(user);
        }
        token = "Bearer " + jwtTokenUtil.generateToken("planner");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/ai/test-plans")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/test-plans").contentType(MediaType.APPLICATION_JSON)
                .content("{\"goal\":\"Open settings\",\"deviceId\":\"emulator-5554\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void validatesInputAndReportsUnreachableModel() throws Exception {
        mvc.perform(post("/api/ai/test-plans").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"hi\",\"deviceId\":\"emulator-5554\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("goal")));
        mvc.perform(post("/api/ai/test-plans").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Open settings\",\"deviceId\":\"emulator-5554\",\"maxSteps\":500}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/test-plans").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Open settings and check Wi-Fi\",\"deviceId\":\"emulator-5554\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("Cannot connect to Ollama")));
    }

    @Test
    void unknownPlansAndBadReviewDecisions() throws Exception {
        mvc.perform(get("/api/ai/test-plans/999999").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/ai/test-plans/999999/review").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"EXECUTED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/test-plans/999999/review").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/ai/test-plans").header("Authorization", token).param("status", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
    }
}
