package com.ksp.agent.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtUtil {
    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;

    public JwtUtil(@Value("${agent.auth.jwt-secret:ksp-agent-default-jwt-secret-change-me-please-1234567890}") String secret,
                   @Value("${agent.auth.jwt-expiration-ms:3600000}") long expirationMs,
                   @Value("${agent.auth.jwt-issuer:ksp-agent}") String issuer) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    public String generateToken(String upn, String displayName, String email) {
        return generateToken(upn, displayName, email, List.of(), List.of());
    }

    /**
     * Mint a JWT that also carries the user's AD group ids and resolved
     * application roles. The SPA decodes {@code roles} (e.g. {@code ["ADMIN","USER"]})
     * and {@code groups} is available for debugging / future fine-grained checks.
     */
    public String generateToken(String upn, String displayName, String email,
                                List<String> groups, List<String> roles) {
        return generateToken(upn, displayName, email, groups, roles, false);
    }

    /**
     * Mint a JWT carrying the user's roles plus a {@code mustChangePassword} boolean claim (only
     * emitted when true) so the SPA can force a password-change flow after a reset / first login.
     */
    public String generateToken(String upn, String displayName, String email,
                                List<String> groups, List<String> roles, boolean mustChangePassword) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        if (displayName != null) {
            claims.put("name", displayName);
        }
        if (email != null) {
            claims.put("email", email);
        }
        if (groups != null && !groups.isEmpty()) {
            claims.put("groups", groups);
        }
        if (roles != null && !roles.isEmpty()) {
            claims.put("roles", roles);
        }
        if (mustChangePassword) {
            claims.put("mustChangePassword", true);
        }
        return Jwts.builder()
                .subject(upn)
                .issuer(issuer)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration() == null || claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
