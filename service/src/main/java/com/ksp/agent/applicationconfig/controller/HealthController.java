package com.ksp.agent.applicationconfig.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness/readiness and gateway health checks. Mounted at the servlet root — not under
 * {@code /api} — so APIM or other layers can protect {@code /api/**} while leaving {@code /}
 * and {@code /health} open.
 */
@RestController
public class HealthController {

    private static final Map<String, String> UP = Map.of("status", "UP");

    @GetMapping({"/", "/health"})
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(UP);
    }
}
