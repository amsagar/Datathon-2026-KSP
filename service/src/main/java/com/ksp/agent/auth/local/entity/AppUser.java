package com.ksp.agent.auth.local.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AppUser {
    private String id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    /** Comma-separated application roles: ADMIN, SUPERVISOR, INVESTIGATOR, ANALYST, POLICYMAKER. */
    private String roles;
    private LocalDate dateOfBirth;
    private String phone;
    private String designation;
    private String department;
    private byte[] photo;
    private String photoContentType;
    private boolean enabled;
    private boolean mustChangePassword;
    private Long lastLoginAt;
    private Long createdAt;
    private Long updatedAt;
}
