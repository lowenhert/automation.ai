package com.lowenhert.patchmanagement.core.user;

import java.util.Map;

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

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
  
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody Map<String, Object> body) {
        ObjectMapper mapper = new ObjectMapper();

        // Safely extract "data" (prevent NullPointerException)
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        if (data == null) {
            throw new IllegalArgumentException("Request body must contain 'data' field");
        }

        // Convert to DTO
        CreateUserRequest request = mapper.convertValue(data, CreateUserRequest.class);

        // Handle missing or optional fields gracefully
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            // Auto-generate username from email or firstName if available
            if (request.getEmail() != null && !request.getEmail().isEmpty()) {
                request.setUsername(request.getEmail().split("@")[0]);
            } else if (request.getFirstName() != null) {
                request.setUsername(request.getFirstName().toLowerCase());
            } else {
                request.setUsername("user" + System.currentTimeMillis());
            }
        }

        // Default provider if not given
        if (request.getProvider() == null) {
            request.setProvider("local");
        }

        // Validate password
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password is required to create a user");
        }

        // Optional: fill other defaults
        if (request.getRole() == null) {
            request.setRole("user");
        }

        // Finally, call service
        return ResponseEntity.ok(userService.createUser(request));
    }



    @GetMapping
    public ResponseEntity<UsersListResponse> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }


    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        CreateUserRequest request = new ObjectMapper().convertValue(data, CreateUserRequest.class);

        return ResponseEntity.ok(userService.updateUser(id, request));
    }
    
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build(); // Unauthorized
        }

        String username = authentication.getName();
        UserResponse user = userService.getUserByUsername(username);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(user);
    }

}
