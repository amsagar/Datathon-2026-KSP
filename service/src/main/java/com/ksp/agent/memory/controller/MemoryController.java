package com.ksp.agent.memory.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.auth.service.SecurityContextService;
import com.ksp.agent.memory.dto.response.SemanticFactDto;
import com.ksp.agent.memory.service.SemanticMemoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manage long-term memory: lets a signed-in user inspect and forget the durable facts the agent has
 * learned about them. All operations are scoped to the authenticated user.
 */
@RestController
@RequestMapping(ApiConstants.MEMORIES_PATH)
public class MemoryController {

    private final SemanticMemoryService semanticMemoryService;
    private final SecurityContextService securityContextService;

    public MemoryController(SemanticMemoryService semanticMemoryService,
                           SecurityContextService securityContextService) {
        this.semanticMemoryService = semanticMemoryService;
        this.securityContextService = securityContextService;
    }

    /** The current user's stored memories, most-recently-used first. */
    @GetMapping
    public ResponseEntity<List<SemanticFactDto>> list() {
        String userId = securityContextService.currentUserIdOrThrow();
        return ResponseEntity.ok(semanticMemoryService.listForUser(userId));
    }

    /** Forget a single memory the user owns. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> forget(@PathVariable String id) {
        String userId = securityContextService.currentUserIdOrThrow();
        semanticMemoryService.forget(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** Forget everything the agent remembers about the user. */
    @DeleteMapping
    public ResponseEntity<Void> forgetAll() {
        String userId = securityContextService.currentUserIdOrThrow();
        semanticMemoryService.forgetAll(userId);
        return ResponseEntity.noContent().build();
    }
}
