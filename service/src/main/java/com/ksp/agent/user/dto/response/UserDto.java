package com.ksp.agent.user.dto.response;

import java.util.List;

public record UserDto(
        String id,
        String username,
        String displayName,
        String email,
        List<String> roles,
        boolean enabled,
        boolean mustChangePassword,
        String dateOfBirth,
        String phone,
        String designation,
        String department,
        boolean hasPhoto,
        Long lastLoginAt,
        Long createdAt,
        Long updatedAt
) {
}
