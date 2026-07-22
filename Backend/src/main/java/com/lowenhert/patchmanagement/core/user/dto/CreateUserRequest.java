package com.lowenhert.patchmanagement.core.user.dto;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String username;
    private String password;
    private String email;
    private String role;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String provider;
    private Boolean active;
}
