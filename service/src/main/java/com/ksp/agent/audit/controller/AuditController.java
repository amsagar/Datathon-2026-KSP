package com.ksp.agent.audit.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.audit.dto.response.AuditPage;
import com.ksp.agent.audit.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiConstants.AUDIT_PATH)
@CrossOrigin(origins = "*")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AuditPage> list(@RequestParam(required = false) String search,
                                          @RequestParam(required = false) String action,
                                          @RequestParam(defaultValue = "50") int limit,
                                          @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(auditService.recent(search, action, limit, offset));
    }
}
