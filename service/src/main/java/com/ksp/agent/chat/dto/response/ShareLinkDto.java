package com.ksp.agent.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Owner-facing view of a session's share link (no message content). */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShareLinkDto {
    private String shareId;
    private int messageCount;
    private Long createdAt;
    private Long updatedAt;
}
