package com.ksp.agent.chat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatToolEvent {
    private String id;
    private String sessionId;
    private int turnIndex;
    private int seq;
    private String callId;
    private String toolName;
    private String toolInput;
    private String toolOutput;
    private boolean error;
    private Long createdAt;
}
