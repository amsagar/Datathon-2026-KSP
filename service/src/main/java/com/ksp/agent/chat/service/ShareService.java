package com.ksp.agent.chat.service;

import com.ksp.agent.chat.dto.response.ShareLinkDto;
import com.ksp.agent.chat.dto.response.SharedChatDto;

import java.util.Optional;

public interface ShareService {

    /** Creates or refreshes the share for a session (owner only), snapshotting the current messages. */
    ShareLinkDto createOrRefresh(String sessionId);

    /** The session's existing share link, if any (owner only). */
    Optional<ShareLinkDto> getForSession(String sessionId);

    /** The frozen snapshot for a share id (any authenticated user). */
    SharedChatDto view(String shareId);

    /** Deletes a share link (owner only). */
    void revoke(String shareId);
}
