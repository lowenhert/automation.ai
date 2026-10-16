package com.lowenhert.patchmanagement.core.screenlearning.exploration;

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

/** HTTP contract of /api/exploration through the real security chain (no device involved). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:exploration-api;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ExplorationApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtTokenUtil jwtTokenUtil;
    @Autowired private UserRepository userRepository;

    private String token;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("explorer").isEmpty()) {
            User user = new User();
            user.setUsername("explorer");
            user.setPassword("not-used");
            user.setRole("USER");
            userRepository.save(user);
        }
        token = "Bearer " + jwtTokenUtil.generateToken("explorer");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/exploration/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/exploration/sessions/1/actions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"DPAD_UP\"}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/exploration/graph").param("deviceId", "emulator-5554")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsUnknownActionsAndOutOfBoundsLimits() throws Exception {
        mvc.perform(post("/api/exploration/sessions/1/actions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"KEYCODE_POWER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/exploration/sessions/1/actions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"input text rm -rf\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/exploration/sessions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"emulator-5554\",\"mode\":\"AUTO\",\"maxSteps\":100000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("maxSteps")));
        mvc.perform(post("/api/exploration/sessions").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"deviceId\":\"emulator-5554\",\"mode\":\"UNLIMITED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/exploration/graph").header("Authorization", token).param("deviceId", "-s"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownSessionsAndNodesAre404AndEmptyGraphIsOk() throws Exception {
        mvc.perform(get("/api/exploration/sessions/999999").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/exploration/sessions/999999/stop").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/exploration/nodes/999999").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/exploration/graph").header("Authorization", token).param("deviceId", "no-such-device"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isEmpty())
                .andExpect(jsonPath("$.edges").isEmpty());
        mvc.perform(get("/api/exploration/sessions").header("Authorization", token).param("size", "500"))
                .andExpect(status().isBadRequest());
    }
}
