package com.ksp.agent.auth.local.api;

import com.ksp.agent.audit.service.AuditService;
import com.ksp.agent.auth.config.JwtUtil;
import com.ksp.agent.auth.local.dto.UserProfileResponse;
import com.ksp.agent.auth.local.entity.AppUser;
import com.ksp.agent.auth.local.repo.AppUserRepository;
import com.ksp.agent.auth.service.SecurityContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Local username/password authentication and self-service profile management. On successful login a
 * JWT is minted with the user's application roles (ADMIN, SUPERVISOR, INVESTIGATOR, ANALYST,
 * POLICYMAKER) in the {@code roles} claim plus a {@code mustChangePassword} flag; {@code JwtAuthFilter}
 * turns the roles into {@code ROLE_*} authorities on every request.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
public class AuthController {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SecurityContextService securityContextService;
    private final AuditService auditService;

    public AuthController(AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          SecurityContextService securityContextService,
                          AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.securityContextService = securityContextService;
        this.auditService = auditService;
    }

    public record LoginRequest(String username, String password) {
    }

    public record RegisterRequest(String username, String password,
                                  String displayName, String email, String roles) {
    }

    public record ProfileUpdateRequest(String displayName, String email, String phone,
                                       String dateOfBirth, String designation, String department) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        if (request == null || request.username() == null || request.password() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
        String username = request.username().trim();
        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(username, "LOGIN_FAILED", username, "Invalid credentials");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }
        if (!user.isEnabled()) {
            auditService.record(username, "LOGIN_FAILED", username, "Account disabled");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Account is disabled. Contact an administrator."));
        }
        userRepository.touchLogin(user.getId(), System.currentTimeMillis());
        auditService.record(username, "LOGIN_SUCCESS", username, null);
        String token = jwtUtil.generateToken(
                user.getUsername(), user.getDisplayName(), user.getEmail(),
                List.of(), rolesOf(user), user.isMustChangePassword());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username and password are required"));
        }
        String username = request.username().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username already exists"));
        }
        String roles = request.roles() == null || request.roles().isBlank()
                ? "ANALYST"
                : request.roles().trim().toUpperCase();
        userRepository.create(username, passwordEncoder.encode(request.password()),
                request.displayName(), request.email(), roles, System.currentTimeMillis());
        log.info("Created user '{}' with roles {}", username, roles);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", username));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me() {
        AppUser user = currentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(toProfile(user));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMe(@RequestBody ProfileUpdateRequest request) {
        AppUser user = currentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String displayName = request.displayName() != null ? request.displayName().trim() : user.getDisplayName();
        String email = request.email() != null ? request.email().trim() : user.getEmail();
        String phone = request.phone() != null ? request.phone().trim() : user.getPhone();
        String designation = request.designation() != null ? request.designation().trim() : user.getDesignation();
        String department = request.department() != null ? request.department().trim() : user.getDepartment();
        LocalDate dob = request.dateOfBirth() != null && !request.dateOfBirth().isBlank()
                ? parseDate(request.dateOfBirth()) : user.getDateOfBirth();
        userRepository.updateProfileAndRoles(user.getId(), displayName, email, user.getRoles(),
                dob, phone, designation, department, user.isEnabled(), System.currentTimeMillis());
        auditService.record(user.getUsername(), "PROFILE_UPDATED", user.getUsername(), null);
        AppUser updated = userRepository.findById(user.getId()).orElse(user);
        return ResponseEntity.ok(toProfile(updated));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody ChangePasswordRequest request) {
        AppUser user = currentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request == null || request.currentPassword() == null || request.newPassword() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current and new password are required"));
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect"));
        }
        if (request.newPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters"));
        }
        userRepository.updatePassword(user.getId(), passwordEncoder.encode(request.newPassword()), false);
        auditService.record(user.getUsername(), "PASSWORD_CHANGED", user.getUsername(), null);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/me/photo")
    public ResponseEntity<Void> uploadMyPhoto(@RequestParam("file") MultipartFile file) throws IOException {
        AppUser user = currentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        userRepository.updatePhoto(user.getId(), file.getBytes(), file.getContentType());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/photo")
    public ResponseEntity<byte[]> myPhoto() {
        AppUser user = currentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return userRepository.findPhoto(user.getId())
                .filter(p -> p.bytes() != null && p.bytes().length > 0)
                .map(p -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_TYPE,
                                p.contentType() != null ? p.contentType() : MediaType.IMAGE_JPEG_VALUE)
                        .body(p.bytes()))
                .orElse(ResponseEntity.noContent().build());
    }

    private AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private UserProfileResponse toProfile(AppUser user) {
        List<String> roles = rolesOf(user);
        String name = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
        String jobTitle = user.getDesignation() != null && !user.getDesignation().isBlank()
                ? user.getDesignation()
                : (roles.isEmpty() ? null : roles.get(0));
        String dob = user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null;
        return new UserProfileResponse(
                name, user.getEmail(), user.getUsername(), jobTitle,
                user.getPhotoContentType() != null ? "/api/v1/auth/photo" : null,
                roles, roles.contains("ADMIN"),
                dob, user.getPhone(), user.getDesignation(), user.getDepartment(),
                user.isEnabled(), user.isMustChangePassword());
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<String> rolesOf(AppUser user) {
        if (user.getRoles() == null || user.getRoles().isBlank()) {
            return List.of("ANALYST");
        }
        return Arrays.stream(user.getRoles().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .toList();
    }
}
