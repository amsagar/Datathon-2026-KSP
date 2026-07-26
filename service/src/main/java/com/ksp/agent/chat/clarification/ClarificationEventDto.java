package com.ksp.agent.chat.clarification;

import java.util.List;

public record ClarificationEventDto(String requestId, String callId,
                                   List<ClarificationQuestionDto> questions) {
}
