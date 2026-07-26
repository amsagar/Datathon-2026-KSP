package com.ksp.agent.chat.tooling;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolEventRegistry {

    public static final String REQUEST_ID = "requestId";

    private final Map<String, ToolEventSink> sinks = new ConcurrentHashMap<>();

    public void register(String requestId, ToolEventSink sink) {
        sinks.put(requestId, sink);
    }

    public void unregister(String requestId) {
        sinks.remove(requestId);
    }

    public Optional<ToolEventSink> get(String requestId) {
        return Optional.ofNullable(sinks.get(requestId));
    }
}
