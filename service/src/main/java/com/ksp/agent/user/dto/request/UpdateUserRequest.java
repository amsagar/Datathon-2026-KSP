package com.ksp.agent.user.dto.request;

import java.util.List;

public record UpdateUserRequest(
        String displayName,
        String email,
        List<String> roles,
        Boolean enabled,
        String dateOfBirth,
        String phone,
        String designation,
        String department
) {
}
