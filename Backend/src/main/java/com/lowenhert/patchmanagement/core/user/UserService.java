package com.lowenhert.patchmanagement.core.user;

import com.lowenhert.patchmanagement.core.user.dto.CreateUserRequest;
import com.lowenhert.patchmanagement.core.user.dto.UserResponse;
import com.lowenhert.patchmanagement.core.user.dto.UsersListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserResponse createUser(CreateUserRequest request) {
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
        return mapToResponse(saved);
    }

    // Updated method to return count + rows
    public UsersListResponse getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return UsersListResponse.builder()
                .count(users.size())
                .rows(users)
                .build();
    }

    public UserResponse getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void deleteUser(Long id) {
        User currentUser = getAuthenticatedUser();
        User targetUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Viewer cannot delete anyone
        if ("VIEWER".equalsIgnoreCase(currentUser.getRole())) {
            throw new RuntimeException("Viewer is not allowed to delete users");
        }

        // User cannot delete himself
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())
                && currentUser.getId().equals(targetUser.getId())) {
            throw new RuntimeException("You cannot delete your own account");
        }

        // Non-admin cannot delete others
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
            throw new RuntimeException("Only admin can delete other users");
        }

        userRepository.deleteById(id);
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("No user currently logged in");
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
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
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Viewer cannot update anyone
        if ("VIEWER".equalsIgnoreCase(currentUser.getRole())) {
            throw new RuntimeException("Viewer is not allowed to update users");
        }

        // Non-admin cannot update other users
        if (!"ADMIN".equalsIgnoreCase(currentUser.getRole())
                && !currentUser.getId().equals(existingUser.getId())) {
            throw new RuntimeException("You are not allowed to update other users");
        }

        // Prevent role change by non-admin
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

        return mapToResponse(userRepository.save(existingUser));
    }

    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToResponse(user);
    }

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Unauthorized");
        }

        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

}
