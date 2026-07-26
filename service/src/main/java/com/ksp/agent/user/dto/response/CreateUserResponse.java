package com.ksp.agent.user.dto.response;

public record CreateUserResponse(
        UserDto user,
        String temporaryPassword
) {
}
