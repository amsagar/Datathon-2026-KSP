package com.ksp.agent.user.dto.request;

import java.util.List;

public record CreateUserRequest(
        String username,
        String displayName,
        String email,
        List<String> roles,
        String dateOfBirth,
        String phone,
        String designation,
        String department,
        Boolean enabled
) {
}
