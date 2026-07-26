package com.ksp.agent.applicationconfig.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness probes for AppSail / gateways. Mounted at the servlet root (not only under
 * {@code /api}) so probes stay open without JWT. Also exposes {@code /api/health} for
 * callers that only route the {@code /api/**} prefix.
 */
@RestController
public class HealthController {

    private static final Map<String, String> UP = Map.of("status", "UP");

    @GetMapping({"/", "/health", "/api/health"})
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(UP);
    }

    /** Some load balancers probe with HEAD only. */
    @RequestMapping(value = {"/", "/health", "/api/health"}, method = RequestMethod.HEAD)
    public ResponseEntity<Void> healthHead() {
        return ResponseEntity.ok().build();
    }
}
