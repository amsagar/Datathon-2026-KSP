package com.ksp.agent.tool.runtime;

import com.ksp.agent.tool.repo.AgentToolRepository;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds runtime {@link ToolCallback}s for the HTTP tools linked to an assistant.
 */
@Component
public class HttpToolCallbackFactory {

    private final AgentToolRepository toolRepository;
    private final HttpToolExecutor executor;

    public HttpToolCallbackFactory(AgentToolRepository toolRepository, HttpToolExecutor executor) {
        this.toolRepository = toolRepository;
        this.executor = executor;
    }

    public List<ToolCallback> callbacksForAssistant(String assistantId) {
        if (assistantId == null || assistantId.isBlank()) {
            return List.of();
        }
        return toolRepository.findEnabledByAssistant(assistantId).stream()
                .map(tool -> (ToolCallback) new HttpToolCallback(tool, executor))
                .toList();
    }
}
