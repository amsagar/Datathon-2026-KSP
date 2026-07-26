package com.ksp.agent.suggestion.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.auth.service.SecurityContextService;
import com.ksp.agent.suggestion.service.PromptSuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Starter-prompt suggestions for the empty chat screen. {@code GET} serves the rotating pool
 * (personalized for the calling user when they have generated rows); {@code POST /generate}
 * (re)generates from assistant info and, optionally, the user's memories + recent chats.
 */
@RestController
@RequestMapping(ApiConstants.ASSISTANTS_PATH + "/prompt-suggestions")
public class PromptSuggestionController {

    private final PromptSuggestionService service;
    private final SecurityContextService securityContextService;

    public PromptSuggestionController(PromptSuggestionService service,
                                      SecurityContextService securityContextService) {
        this.service = service;
        this.securityContextService = securityContextService;
    }

    @GetMapping
    public ResponseEntity<List<String>> list(@RequestParam String assistantId,
                                             @RequestParam(required = false, defaultValue = "en") String lang) {
        String userId = currentUserOrNull();
        return ResponseEntity.ok(service.serve(assistantId, userId, lang));
    }

    @PostMapping("/generate")
    public ResponseEntity<List<String>> generate(
            @RequestParam String assistantId,
            @RequestParam(required = false, defaultValue = "en") String lang,
            @RequestParam(required = false, defaultValue = "true") boolean personalized) {
        String userId = currentUserOrNull();
        return ResponseEntity.ok(service.generate(assistantId, userId, lang, personalized));
    }

    private String currentUserOrNull() {
        try {
            return securityContextService.currentUserIdOrThrow();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
