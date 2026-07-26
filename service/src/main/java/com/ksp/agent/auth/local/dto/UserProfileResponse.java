package com.ksp.agent.auth.local.dto;

import java.util.List;

public record UserProfileResponse(
        String name,
        String email,
        String upn,
        String jobTitle,
        String photoUrl,
        List<String> roles,
        boolean admin,
        String dateOfBirth,
        String phone,
        String designation,
        String department,
        boolean enabled,
        boolean mustChangePassword
) {
}
