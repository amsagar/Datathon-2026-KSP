package com.ksp.agent.user.service.impl;

import com.ksp.agent.applicationconfig.exceptions.DuplicateResourceException;
import com.ksp.agent.applicationconfig.exceptions.ResourceNotFoundException;
import com.ksp.agent.audit.service.AuditService;
import com.ksp.agent.auth.local.entity.AppUser;
import com.ksp.agent.auth.local.repo.AppUserRepository;
import com.ksp.agent.auth.service.SecurityContextService;
import com.ksp.agent.user.dto.request.CreateUserRequest;
import com.ksp.agent.user.dto.request.UpdateUserRequest;
import com.ksp.agent.user.dto.response.CreateUserResponse;
import com.ksp.agent.user.dto.response.ResetPasswordResponse;
import com.ksp.agent.user.dto.response.UserDto;
import com.ksp.agent.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final Set<String> ALLOWED_ROLES =
            Set.of("ADMIN", "SUPERVISOR", "INVESTIGATOR", "ANALYST", "POLICYMAKER");

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityContextService securityContextService;

    public UserServiceImpl(AppUserRepository repository,
                           PasswordEncoder passwordEncoder,
                           AuditService auditService,
                           SecurityContextService securityContextService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.securityContextService = securityContextService;
    }

    @Override
    public List<UserDto> list(String search, String role, String status) {
        String needle = search == null ? null : search.trim().toLowerCase();
        String roleFilter = role == null || role.isBlank() ? null : role.trim().toUpperCase();
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toLowerCase();
        return repository.findAll().stream()
                .filter(u -> matchesSearch(u, needle))
                .filter(u -> roleFilter == null || rolesOf(u).contains(roleFilter))
                .filter(u -> matchesStatus(u, statusFilter))
                .map(this::toDto)
                .toList();
    }

    @Override
    public UserDto get(String id) {
        return toDto(requireUser(id));
    }

    @Override
    public CreateUserResponse create(CreateUserRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        String username = request.username().trim();
        if (repository.findByUsername(username).isPresent()) {
            throw new DuplicateResourceException("Username already exists: " + username);
        }
        LocalDate dob = parseDob(request.dateOfBirth());
        if (dob == null) {
            throw new IllegalArgumentException("dateOfBirth is required (yyyy-MM-dd)");
        }
        String roles = normalizeRoles(request.roles());
        boolean enabled = request.enabled() == null || request.enabled();
        String tempPassword = temporaryPassword(username, dob);
        long now = System.currentTimeMillis();
        String id = repository.create(username, passwordEncoder.encode(tempPassword),
                request.displayName(), request.email(), roles, dob, request.phone(),
                request.designation(), request.department(), enabled, true, now);
        auditService.record(currentActor(), "USER_CREATED", username,
                "Created user with roles " + roles);
        return new CreateUserResponse(get(id), tempPassword);
    }

    @Override
    public UserDto update(String id, UpdateUserRequest request) {
        AppUser existing = requireUser(id);

        String newDisplayName = request.displayName() != null ? request.displayName() : existing.getDisplayName();
        String newEmail = request.email() != null ? request.email() : existing.getEmail();
        String newRoles = request.roles() != null ? normalizeRoles(request.roles()) : existing.getRoles();
        boolean newEnabled = request.enabled() != null ? request.enabled() : existing.isEnabled();
        LocalDate newDob = request.dateOfBirth() != null ? parseDob(request.dateOfBirth()) : existing.getDateOfBirth();
        if (request.dateOfBirth() != null && newDob == null) {
            throw new IllegalArgumentException("Invalid dateOfBirth (expected yyyy-MM-dd)");
        }
        String newPhone = request.phone() != null ? request.phone() : existing.getPhone();
        String newDesignation = request.designation() != null ? request.designation() : existing.getDesignation();
        String newDepartment = request.department() != null ? request.department() : existing.getDepartment();

        boolean rolesChanged = !equalsRoles(existing.getRoles(), newRoles);
        boolean enabledChanged = existing.isEnabled() != newEnabled;

        // Guard: cannot deactivate yourself.
        if (enabledChanged && !newEnabled && isSelf(existing)) {
            throw new IllegalArgumentException("You cannot deactivate your own account");
        }
        // Guard: cannot disable or remove ADMIN from the last enabled admin.
        boolean wasEnabledAdmin = existing.isEnabled() && rolesOf(existing).contains("ADMIN");
        boolean willBeEnabledAdmin = newEnabled && rolesOf(newRoles).contains("ADMIN");
        if (wasEnabledAdmin && !willBeEnabledAdmin && repository.countEnabledAdmins() <= 1) {
            throw new IllegalArgumentException("Cannot remove or disable the last enabled admin");
        }

        repository.updateProfileAndRoles(id, newDisplayName, newEmail, newRoles, newDob,
                newPhone, newDesignation, newDepartment, newEnabled, System.currentTimeMillis());

        String actor = currentActor();
        if (rolesChanged) {
            auditService.record(actor, "USER_ROLE_CHANGED", existing.getUsername(),
                    "Roles changed to " + newRoles);
        }
        if (enabledChanged) {
            auditService.record(actor, newEnabled ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                    existing.getUsername(), null);
        }
        if (!rolesChanged && !enabledChanged) {
            auditService.record(actor, "USER_UPDATED", existing.getUsername(), null);
        }
        return get(id);
    }

    @Override
    public void delete(String id) {
        AppUser existing = requireUser(id);
        if (isSelf(existing)) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        boolean isEnabledAdmin = existing.isEnabled() && rolesOf(existing).contains("ADMIN");
        if (isEnabledAdmin && repository.countEnabledAdmins() <= 1) {
            throw new IllegalArgumentException("Cannot delete the last enabled admin");
        }
        repository.delete(id);
        auditService.record(currentActor(), "USER_DELETED", existing.getUsername(), null);
    }

    @Override
    public ResetPasswordResponse resetPassword(String id) {
        AppUser existing = requireUser(id);
        LocalDate dob = existing.getDateOfBirth();
        if (dob == null) {
            throw new IllegalArgumentException("Cannot reset password: user has no date of birth on record");
        }
        String tempPassword = temporaryPassword(existing.getUsername(), dob);
        repository.updatePassword(id, passwordEncoder.encode(tempPassword), true);
        auditService.record(currentActor(), "USER_PASSWORD_RESET", existing.getUsername(), null);
        return new ResetPasswordResponse(tempPassword);
    }

    @Override
    public void uploadPhoto(String id, byte[] bytes, String contentType) {
        requireUser(id);
        repository.updatePhoto(id, bytes, contentType);
        auditService.record(currentActor(), "USER_PHOTO_UPDATED", id, null);
    }

    @Override
    public AppUserRepository.UserPhoto getPhoto(String id) {
        requireUser(id);
        return repository.findPhoto(id).orElse(null);
    }

    // --- helpers ---

    private AppUser requireUser(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /** first4 of username (lowercased) + birth year, e.g. "rkumar"+1990 -> "rkum1990". */
    private String temporaryPassword(String username, LocalDate dob) {
        String lower = username.toLowerCase();
        String first4 = lower.length() >= 4 ? lower.substring(0, 4) : lower;
        return first4 + dob.getYear();
    }

    private LocalDate parseDob(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return "ANALYST";
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String r : roles) {
            if (r == null || r.isBlank()) {
                continue;
            }
            String upper = r.trim().toUpperCase();
            if (!ALLOWED_ROLES.contains(upper)) {
                throw new IllegalArgumentException("Invalid role: " + r);
            }
            normalized.add(upper);
        }
        if (normalized.isEmpty()) {
            return "ANALYST";
        }
        return String.join(",", normalized);
    }

    private List<String> rolesOf(AppUser user) {
        return rolesOf(user.getRoles());
    }

    private List<String> rolesOf(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toUpperCase).toList();
    }

    private boolean equalsRoles(String a, String b) {
        return new LinkedHashSet<>(rolesOf(a)).equals(new LinkedHashSet<>(rolesOf(b)));
    }

    private boolean isSelf(AppUser user) {
        try {
            return user.getUsername() != null
                    && user.getUsername().equalsIgnoreCase(securityContextService.currentUserIdOrThrow());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String currentActor() {
        try {
            return securityContextService.currentUserIdOrThrow();
        } catch (RuntimeException e) {
            return "system";
        }
    }

    private boolean matchesSearch(AppUser u, String needle) {
        if (needle == null || needle.isEmpty()) {
            return true;
        }
        return contains(u.getUsername(), needle)
                || contains(u.getDisplayName(), needle)
                || contains(u.getEmail(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    private boolean matchesStatus(AppUser u, String statusFilter) {
        if (statusFilter == null) {
            return true;
        }
        if (statusFilter.equals("enabled") || statusFilter.equals("active")) {
            return u.isEnabled();
        }
        if (statusFilter.equals("disabled") || statusFilter.equals("inactive")) {
            return !u.isEnabled();
        }
        return true;
    }

    private UserDto toDto(AppUser u) {
        return new UserDto(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                u.getEmail(),
                rolesOf(u),
                u.isEnabled(),
                u.isMustChangePassword(),
                u.getDateOfBirth() != null ? u.getDateOfBirth().toString() : null,
                u.getPhone(),
                u.getDesignation(),
                u.getDepartment(),
                u.getPhotoContentType() != null,
                u.getLastLoginAt(),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }
}
