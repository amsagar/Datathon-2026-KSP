package com.ksp.agent.chat.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.chat.dto.request.UpdateSessionRequest;
import com.ksp.agent.chat.dto.response.ChatMessageDto;
import com.ksp.agent.chat.dto.response.ChatSessionDto;
import com.ksp.agent.chat.dto.response.ShareLinkDto;
import com.ksp.agent.chat.dto.response.SupervisorSessionSummaryDto;
import com.ksp.agent.chat.service.ChatSessionService;
import com.ksp.agent.chat.service.ShareService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(ApiConstants.SESSIONS_PATH)
@CrossOrigin(origins = "*")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ShareService shareService;

    public ChatSessionController(ChatSessionService chatSessionService, ShareService shareService) {
        this.chatSessionService = chatSessionService;
        this.shareService = shareService;
    }

    @PostMapping
    public ResponseEntity<ChatSessionDto> create(
            @RequestParam(required = false) String assistantId,
            @RequestParam(defaultValue = "false") boolean temporary) {
        return ResponseEntity.ok(chatSessionService.create(assistantId, temporary));
    }

    @GetMapping
    public ResponseEntity<List<ChatSessionDto>> list(
            @RequestParam(defaultValue = "false") boolean archived) {
        return ResponseEntity.ok(chatSessionService.list(archived));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<ChatMessageDto>> messages(@PathVariable String id) {
        return ResponseEntity.ok(chatSessionService.messages(id));
    }

    /**
     * Cross-user session listing for supervisor/admin oversight (Phase 4.4) — lets a supervisor
     * find a session id to open via {@link #messages}, which itself re-checks the caller's role
     * before returning another user's transcript.
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERVISOR')")
    public ResponseEntity<List<SupervisorSessionSummaryDto>> listAllForSupervisor(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(chatSessionService.listAllForSupervisor(limit));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ChatSessionDto> update(@PathVariable String id,
                                                 @RequestBody UpdateSessionRequest request) {
        return ResponseEntity.ok(chatSessionService.update(id, request));
    }

    @PostMapping("/{id}/truncate")
    public ResponseEntity<Void> truncate(@PathVariable String id,
                                         @RequestParam int messageIndex) {
        chatSessionService.truncateFrom(id, messageIndex);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        chatSessionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Create or refresh this session's view-only share snapshot (owner only). */
    @PostMapping("/{id}/share")
    public ResponseEntity<ShareLinkDto> createShare(@PathVariable String id) {
        return ResponseEntity.ok(shareService.createOrRefresh(id));
    }

    /** This session's existing share link, or 204 if none (owner only). */
    @GetMapping("/{id}/share")
    public ResponseEntity<ShareLinkDto> getShare(@PathVariable String id) {
        return shareService.getForSession(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
