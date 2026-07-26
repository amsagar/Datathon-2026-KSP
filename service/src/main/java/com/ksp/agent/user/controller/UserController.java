package com.ksp.agent.user.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.auth.local.repo.AppUserRepository;
import com.ksp.agent.user.dto.request.CreateUserRequest;
import com.ksp.agent.user.dto.request.UpdateUserRequest;
import com.ksp.agent.user.dto.response.CreateUserResponse;
import com.ksp.agent.user.dto.response.ResetPasswordResponse;
import com.ksp.agent.user.dto.response.UserDto;
import com.ksp.agent.user.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(ApiConstants.USERS_PATH)
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDto>> list(@RequestParam(required = false) String search,
                                              @RequestParam(required = false) String role,
                                              @RequestParam(required = false) String status) {
        return ResponseEntity.ok(userService.list(search, role, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> get(@PathVariable String id) {
        return ResponseEntity.ok(userService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreateUserResponse> create(@RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDto> update(@PathVariable String id,
                                          @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@PathVariable String id) {
        return ResponseEntity.ok(userService.resetPassword(id));
    }

    @PostMapping("/{id}/photo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> uploadPhoto(@PathVariable String id,
                                            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        userService.uploadPhoto(id, file.getBytes(), file.getContentType());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/photo")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> getPhoto(@PathVariable String id) {
        AppUserRepository.UserPhoto photo = userService.getPhoto(id);
        if (photo == null || photo.bytes() == null || photo.bytes().length == 0) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = photo.contentType() != null
                ? MediaType.parseMediaType(photo.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(mediaType).body(photo.bytes());
    }
}
