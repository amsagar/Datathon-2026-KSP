package com.ksp.agent.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatSession {
    private String id;
    private String title;
    private boolean archived;
    private String assistantId;
    private String styleId;
    /** Pinned LLM provider (llm_provider.id); null = platform default. */
    private String providerId;
    private Long createdAt;
    private Long updatedAt;
    /** Temporary chat: persisted + viewable, but auto-deleted after the retention window and isolated
     *  from long-term semantic memory. */
    private boolean temporary;
}
