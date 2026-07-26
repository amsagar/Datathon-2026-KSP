package com.ksp.agent.auth.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "agent.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("*");
    private List<String> allowedMethods = List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS");
    private List<String> allowedHeaders = List.of("*");
    private List<String> exposedHeaders = List.of();
    private boolean allowCredentials = false;
    private long maxAge = 3600L;
    private String pathPattern = "/api/**";

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /**
     * Accepts YAML lists or a single {@code CORS_ALLOWED_ORIGINS} env value that may be
     * comma-separated (common on AppSail). Also strips trailing slashes so
     * {@code https://ui.example/} still matches the browser's {@code Origin}.
     */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        if (allowedOrigins == null) {
            this.allowedOrigins = List.of();
            return;
        }
        List<String> normalized = new ArrayList<>();
        for (String entry : allowedOrigins) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Arrays.stream(entry.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                    .forEach(normalized::add);
        }
        this.allowedOrigins = List.copyOf(normalized);
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }
}
