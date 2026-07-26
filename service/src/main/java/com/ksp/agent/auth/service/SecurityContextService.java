package com.ksp.agent.auth.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class SecurityContextService {

    /**
     * Returns the authenticated user's UPN (the Entra User Principal Name carried as the JWT
     * subject). Throws {@link IllegalArgumentException} when no authenticated principal is
     * present — every protected endpoint should already have been gated by the SAML/JWT
     * filter chain, so this is a defensive check.
     */
    public String currentUserIdOrThrow() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || auth.getName().isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return auth.getName();
    }

    public boolean isAdmin() {
        return hasAnyRole("ADMIN");
    }

    /**
     * Plain role names (no {@code ROLE_} prefix) for the authenticated principal, e.g.
     * {@code {"INVESTIGATOR"}}. Empty when unauthenticated. Must be called on the request thread —
     * see the class-level note on {@link #currentUserIdOrThrow()}; tool-execution threads see an
     * empty {@link SecurityContextHolder} and must have the role set captured and closed over
     * instead (mirrors {@code MemoryToolFactory}'s identity-capture pattern).
     */
    public Set<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Set.of();
        }
        Set<String> roles = new HashSet<>();
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String name = authority.getAuthority();
            roles.add(name != null && name.startsWith("ROLE_") ? name.substring(5) : name);
        }
        return roles;
    }

    public boolean hasAnyRole(String... roles) {
        Set<String> current = currentRoles();
        for (String role : roles) {
            if (current.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
