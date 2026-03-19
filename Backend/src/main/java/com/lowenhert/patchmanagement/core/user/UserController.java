package com.lowenhert.patchmanagement.core.user;

import java.util.Map;

import com.lowenhert.patchmanagement.core.config.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowenhert.patchmanagement.core.user.dto.CreateUserRequest;
import com.lowenhert.patchmanagement.core.user.dto.UserResponse;
import com.lowenhert.patchmanagement.core.user.dto.UsersListResponse;

import lombok.RequiredArgsConstructor;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
  
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody Map<String, Object> body) {
        log.info("Received create user request");
        ObjectMapper mapper = new ObjectMapper();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        if (data == null) {
            throw new BadRequestException("Request body must contain 'data' field");
        }

        CreateUserRequest request = mapper.convertValue(data, CreateUserRequest.class);

        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            if (request.getEmail() != null && !request.getEmail().isEmpty()) {
                request.setUsername(request.getEmail().split("@")[0]);
            } else if (request.getFirstName() != null) {
                request.setUsername(request.getFirstName().toLowerCase());
            } else {
                request.setUsername("user" + System.currentTimeMillis());
            }
        }

        if (request.getProvider() == null) {
            request.setProvider("local");
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new BadRequestException("Password is required to create a user");
        }

        if (request.getRole() == null) {
            request.setRole("user");
        }

        UserResponse created = userService.createUser(request);
        log.info("User created successfully: {}", created.getUsername());
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<UsersListResponse> getAllUsers() {
        log.debug("Fetching all users");
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        log.debug("Fetching user by id: {}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("Delete user request for id: {}", id);
        userService.deleteUser(id);
        log.info("User deleted: {}", id);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        log.info("Update user request for id: {}", id);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        if (data == null) {
            throw new BadRequestException("Request body must contain 'data' field");
        }
        CreateUserRequest request = new ObjectMapper().convertValue(data, CreateUserRequest.class);

        UserResponse updated = userService.updateUser(id, request);
        log.info("User updated successfully: {}", id);
        return ResponseEntity.ok(updated);
    }
    
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("Unauthenticated /me request");
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();
        log.debug("Fetching current user profile: {}", username);
        UserResponse user = userService.getUserByUsername(username);
        return ResponseEntity.ok(user);
    }

}
