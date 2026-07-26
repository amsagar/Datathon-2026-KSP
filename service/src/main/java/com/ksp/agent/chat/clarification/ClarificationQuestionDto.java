package com.ksp.agent.chat.clarification;

import java.util.List;

public record ClarificationQuestionDto(
        String question,
        String header,
        boolean multiSelect,
        List<ClarificationOptionDto> options
) {
}
