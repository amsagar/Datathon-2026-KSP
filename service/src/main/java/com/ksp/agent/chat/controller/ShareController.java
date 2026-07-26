package com.ksp.agent.chat.controller;

import com.ksp.agent.applicationconfig.constants.ApiConstants;
import com.ksp.agent.chat.dto.response.SharedChatDto;
import com.ksp.agent.chat.service.ShareService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only access to shared conversation snapshots. Both endpoints sit under the default
 * {@code authenticated()} security rule, so only signed-in users can view a share. Viewing
 * is allowed for any authenticated user; revoking is restricted to the share's owner.
 */
@RestController
@RequestMapping(ApiConstants.SHARES_PATH)
@CrossOrigin(origins = "*")
public class ShareController {

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/{shareId}")
    public ResponseEntity<SharedChatDto> view(@PathVariable String shareId) {
        return ResponseEntity.ok(shareService.view(shareId));
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revoke(@PathVariable String shareId) {
        shareService.revoke(shareId);
        return ResponseEntity.noContent().build();
    }
}
