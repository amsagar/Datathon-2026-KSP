package com.ksp.agent.user.service;

import com.ksp.agent.auth.local.repo.AppUserRepository;
import com.ksp.agent.user.dto.request.CreateUserRequest;
import com.ksp.agent.user.dto.request.UpdateUserRequest;
import com.ksp.agent.user.dto.response.CreateUserResponse;
import com.ksp.agent.user.dto.response.ResetPasswordResponse;
import com.ksp.agent.user.dto.response.UserDto;

import java.util.List;

public interface UserService {

    List<UserDto> list(String search, String role, String status);

    UserDto get(String id);

    CreateUserResponse create(CreateUserRequest request);

    UserDto update(String id, UpdateUserRequest request);

    void delete(String id);

    ResetPasswordResponse resetPassword(String id);

    void uploadPhoto(String id, byte[] bytes, String contentType);

    AppUserRepository.UserPhoto getPhoto(String id);
}
