package com.ksp.agent.chat.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.auth.service.SecurityContextService;
import com.ksp.agent.chat.dto.response.UsageBreakdownRowDto;
import com.ksp.agent.chat.dto.response.UsageSummaryResponse;
import com.ksp.agent.chat.service.LlmUsageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping(ApiConstants.USAGE_PATH)
public class UsageController {

    private final LlmUsageService llmUsageService;
    private final SecurityContextService securityContextService;

    public UsageController(LlmUsageService llmUsageService,
                           SecurityContextService securityContextService) {
        this.llmUsageService = llmUsageService;
        this.securityContextService = securityContextService;
    }

    @GetMapping("/summary")
    public ResponseEntity<UsageSummaryResponse> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(llmUsageService.summary(from, to, scopedUserId()));
    }

    @GetMapping("/by-model")
    public ResponseEntity<List<UsageBreakdownRowDto>> byModel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(llmUsageService.byModel(from, to, scopedUserId()));
    }

    @GetMapping("/by-user")
    public ResponseEntity<List<UsageBreakdownRowDto>> byUser(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        String scope = securityContextService.isAdmin() ? null : securityContextService.currentUserIdOrThrow();
        return ResponseEntity.ok(llmUsageService.byUser(from, to, scope));
    }

    @GetMapping("/by-assistant")
    public ResponseEntity<List<UsageBreakdownRowDto>> byAssistant(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(llmUsageService.byAssistant(from, to, scopedUserId()));
    }

    @GetMapping("/by-source")
    public ResponseEntity<List<UsageBreakdownRowDto>> bySource(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(llmUsageService.bySource(from, to, scopedUserId()));
    }

    @GetMapping("/hourly")
    public ResponseEntity<List<UsageBreakdownRowDto>> hourly(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(llmUsageService.hourly(from, to, scopedUserId()));
    }

    /** Null = org-wide (admin); non-null = single user scope. */
    private String scopedUserId() {
        return securityContextService.isAdmin() ? null : securityContextService.currentUserIdOrThrow();
    }
}
