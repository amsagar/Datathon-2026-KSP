package com.ksp.agent.alert.controller;

import com.ksp.agent.alert.entity.Alert;
import com.ksp.agent.alert.service.AlertService;
import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.audit.service.AuditService;
import com.ksp.agent.auth.service.SecurityContextService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Real early-warning alerts (Phase 4.12) — {@code AlertEvaluationJob} opens these on a schedule;
 * this controller is the lifecycle surface (view/acknowledge/assign/resolve). Visible to every
 * authenticated role (a policymaker needs to SEE alerts), but acting on one (acknowledge/assign/
 * resolve) requires an investigative role — a policymaker viewing an alert isn't the one meant to
 * clear it.
 */
@RestController
@RequestMapping(ApiConstants.ALERTS_PATH)
public class AlertController {

    private static final String INVESTIGATIVE_ROLES = "hasAnyRole('ADMIN','SUPERVISOR','INVESTIGATOR')";

    private final AlertService alertService;
    private final AuditService auditService;
    private final SecurityContextService securityContextService;

    public AlertController(AlertService alertService, AuditService auditService,
                           SecurityContextService securityContextService) {
        this.alertService = alertService;
        this.auditService = auditService;
        this.securityContextService = securityContextService;
    }

    @GetMapping
    public List<Alert> list(@RequestParam(required = false) String status,
                            @RequestParam(defaultValue = "100") int limit) {
        return alertService.list(status, limit);
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize(INVESTIGATIVE_ROLES)
    public Alert acknowledge(@PathVariable long id) {
        audit("ACKNOWLEDGE_ALERT", String.valueOf(id));
        return alertService.acknowledge(id);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(INVESTIGATIVE_ROLES)
    public Alert resolve(@PathVariable long id) {
        audit("RESOLVE_ALERT", String.valueOf(id));
        return alertService.resolve(id);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize(INVESTIGATIVE_ROLES)
    public Alert assign(@PathVariable long id, @RequestBody Map<String, String> body) {
        audit("ASSIGN_ALERT", String.valueOf(id));
        return alertService.assign(id, body.get("assignedTo"));
    }

    private void audit(String action, String target) {
        String actor;
        try {
            actor = securityContextService.currentUserIdOrThrow();
        } catch (RuntimeException e) {
            actor = "unknown";
        }
        auditService.record(actor, action, target, null);
    }
}
