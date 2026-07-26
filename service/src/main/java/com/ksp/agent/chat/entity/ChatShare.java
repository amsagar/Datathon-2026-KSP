package com.ksp.agent.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A frozen, view-only snapshot of a conversation, shareable to other authenticated users.
 * {@code messagesJson} is the rendered message list captured at share time (tool cards stripped),
 * so messages added to the session afterward never appear in the share.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatShare {
    private String id;
    private String sessionId;
    private String createdBy;
    private String title;
    private String assistantName;
    private String messagesJson;
    private int messageCount;
    private Long createdAt;
    private Long updatedAt;
}
