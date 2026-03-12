package com.lowenhert.patchmanagement.core.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String role;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String provider;

    private boolean active;
    private boolean enabled;
    private boolean emailVerified;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
