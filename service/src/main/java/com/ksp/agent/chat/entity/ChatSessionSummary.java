package com.ksp.agent.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatSessionSummary {
    private String sessionId;
    private String summary;
    /** Number of messages from the head of the transcript already folded into {@link #summary}. */
    private int summarizedThroughCount;
    private Long updatedAt;
}
