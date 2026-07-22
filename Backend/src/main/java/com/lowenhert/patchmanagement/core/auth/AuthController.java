package com.lowenhert.patchmanagement.core.auth;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import com.lowenhert.patchmanagement.core.user.User;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BadRequestException("Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadRequestException("Password is required");
        }

        log.info("Login request received for user: {}", request.getUsername());
        AuthService.LoginResult loginResult = authService.login(request.getUsername(), request.getPassword());
        log.info("Login successful for user: {}", request.getUsername());
        return ResponseEntity.ok(new AuthResponse(
            loginResult.getToken(),
            loginResult.getUser().getUsername(),
            loginResult.getUser().getRole(),
            loginResult.getUser().getFirstName(),
            loginResult.getUser().getLastName()
        ));
    }

    @Data
    public static class AuthRequest {
        private String username;
        private String password;
    }

    @Data
    public static class AuthResponse {
        private final String token;
        private final String username;
        private final String role;
        private final String firstName;
        private final String lastName;
    }
}
