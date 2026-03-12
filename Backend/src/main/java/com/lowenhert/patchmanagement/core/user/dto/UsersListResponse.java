package com.lowenhert.patchmanagement.core.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UsersListResponse {
    private int count;
    private List<UserResponse> rows;
}
