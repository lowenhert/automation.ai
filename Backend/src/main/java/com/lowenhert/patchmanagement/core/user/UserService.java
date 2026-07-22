package com.lowenhert.patchmanagement.core.user;

import com.lowenhert.patchmanagement.core.config.exception.ForbiddenException;
import com.lowenhert.patchmanagement.core.config.exception.ResourceNotFoundException;
import com.lowenhert.patchmanagement.core.config.exception.UnauthorizedException;
import com.lowenhert.patchmanagement.core.user.dto.CreateUserRequest;
import com.lowenhert.patchmanagement.core.user.dto.UserResponse;
import com.lowenhert.patchmanagement.core.user.dto.UsersListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse createUser(CreateUserRequest request) {
        log.debug("Creating user: {}", request.getUsername());
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(request.getRole())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phoneNumber(request.getPhoneNumber())
                .provider(request.getProvider())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .active(true)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("User created: id={}, username={}", saved.getId(), saved.getUsername());
        return mapToResponse(saved);
    }

    public UsersListResponse getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        log.debug("Fetched {} users", users.size());
        return UsersListResponse.builder()
                .count(users.size())
                .rows(users)
                .build();
    }

    public UserResponse getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> {
                    log.warn("User not found: id={}", id);
                    return new ResourceNotFoundException("User not found with id: " + id);
                });
    }

    public void deleteUser(Long id) {
        User currentUser = getAuthenticatedUser();
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Delete failed — user not found: id={}", id);
                    return new ResourceNotFoundException("User not found with id: " + id);
                });

        if ("VIEWER".equalsIgnoreCase(currentUser.getRole())) {
            log.warn("Viewer {} attempted to delete user {}", currentUser.getUsername(), id);
            throw new ForbiddenException("Viewer is not allowed to delete users");
        }

        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())
                && currentUser.getId().equals(targetUser.getId())) {
            log.warn("Non-admin user {} attempted to delete own account", currentUser.getUsername());
            throw new ForbiddenException("You cannot delete your own account");
        }

        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            log.warn("Non-admin user {} attempted to delete user {}", currentUser.getUsername(), id);
            throw new ForbiddenException("Only admin can delete other users");
        }

        userRepository.deleteById(id);
        log.info("User deleted: id={} by {}", id, currentUser.getUsername());
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("No user currently logged in");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .provider(user.getProvider())
                .active(user.isActive())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    public UserResponse updateUser(Long id, CreateUserRequest request) {
        User currentUser = getAuthenticatedUser();
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Update failed — user not found: id={}", id);
                    return new ResourceNotFoundException("User not found with id: " + id);
                });

        if ("VIEWER".equalsIgnoreCase(currentUser.getRole())) {
            log.warn("Viewer {} attempted to update user {}", currentUser.getUsername(), id);
            throw new ForbiddenException("Viewer is not allowed to update users");
        }

        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())
                && !currentUser.getId().equals(existingUser.getId())) {
            log.warn("Non-admin user {} attempted to update user {}", currentUser.getUsername(), id);
            throw new ForbiddenException("You are not allowed to update other users");
        }

        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            request.setRole(existingUser.getRole());
        }

        if (request.getUsername() != null) existingUser.setUsername(request.getUsername());
        if (request.getEmail() != null) existingUser.setEmail(request.getEmail());
        if (request.getFirstName() != null) existingUser.setFirstName(request.getFirstName());
        if (request.getLastName() != null) existingUser.setLastName(request.getLastName());
        if (request.getPhoneNumber() != null) existingUser.setPhoneNumber(request.getPhoneNumber());
        if (request.getProvider() != null) existingUser.setProvider(request.getProvider());

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getActive() != null) {
            existingUser.setActive(request.getActive());
        }

        if (request.getRole() != null && "ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            existingUser.setRole(request.getRole());
        }

        User saved = userRepository.save(existingUser);
        log.info("User updated: id={} by {}", id, currentUser.getUsername());
        return mapToResponse(saved);
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found by username: {}", username);
                    return new ResourceNotFoundException("User not found: " + username);
                });
        return mapToResponse(user);
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Not authenticated");
        }

        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found: " + auth.getName()));
    }

}
