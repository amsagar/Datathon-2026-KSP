package com.ksp.agent.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatSessionDto {
    private String id;
    private String title;
    private boolean archived;
    private String assistantId;
    private String styleId;
    private String providerId;
    private Long createdAt;
    private Long updatedAt;
    /** Temporary chat: persisted + viewable, auto-deleted after the retention window, memory-isolated. */
    private boolean temporary;
}
