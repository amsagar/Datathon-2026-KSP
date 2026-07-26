package com.ksp.agent.chat.clarification;

import java.util.Map;

public record SubmitClarificationRequest(String requestId, Map<String, String> answers) {
}
