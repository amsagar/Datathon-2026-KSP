package com.ksp.agent.chat.tooling;

import com.ksp.agent.chat.audit.ChatAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Meta-tool exposed to the model in place of a large tool catalog. Given a natural-language query,
 * returns the most relevant tools (name + description + input schema) so the model can then call
 * {@link InvokeToolCallback}. This keeps the per-request tool count constant regardless of how many
 * tools an assistant actually has.
 */
@Component
public class SearchToolsCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(SearchToolsCallback.class);

    public static final String NAME = "search_tools";

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "Natural-language description of the task you want to accomplish."
                },
                "maxResults": {
                  "type": "integer",
                  "description": "Optional maximum number of tools to return."
                }
              },
              "required": ["query"]
            }
            """;

    private final DynamicToolRegistry registry;
    private final ToolRankingService rankingService;
    private final ObjectMapper objectMapper;
    private final int defaultMaxResults;

    public SearchToolsCallback(DynamicToolRegistry registry,
                               ToolRankingService rankingService,
                               ObjectMapper objectMapper,
                               @Value("${agent.tool-search.max-results:10}") int defaultMaxResults) {
        this.registry = registry;
        this.rankingService = rankingService;
        this.objectMapper = objectMapper;
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Search the available tools by a natural-language query describing what you "
                        + "want to do. Returns the most relevant tools with their exact name, description "
                        + "and input JSON schema. Call this FIRST to discover which tools exist, then call "
                        + "invoke_tool with the chosen tool's name and arguments.")
                .inputSchema(INPUT_SCHEMA)
                .build();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String requestId = resolveRequestId(toolContext);
        DynamicToolRegistry.Entry registryEntry = registry.get(requestId);
        Map<String, ToolCallback> catalog = registryEntry == null ? Map.of() : registryEntry.tools();
        String assistantId = registryEntry == null ? null : registryEntry.assistantId();

        String query = toolInput;
        int max = defaultMaxResults;
        try {
            Map<?, ?> in = objectMapper.readValue(toolInput, Map.class);
            Object q = in.get("query");
            if (q != null) {
                query = String.valueOf(q);
            }
            if (in.get("maxResults") instanceof Number n) {
                max = Math.max(1, n.intValue());
            }
        } catch (Exception e) {
            log.debug("search_tools: could not parse input as JSON, treating raw string as query");
        }

        List<ToolCallback> matches = rankingService.rank(query, assistantId, catalog.values(), max);
        List<String> matchedNames = matches.stream()
                .map(t -> t.getToolDefinition().name())
                .toList();
        ChatAuditLog.searchTools(query, matchedNames);
        List<Map<String, String>> tools = new ArrayList<>();
        for (ToolCallback t : matches) {
            ToolDefinition def = t.getToolDefinition();
            Map<String, String> entry = new java.util.LinkedHashMap<>();
            entry.put("name", def.name());
            entry.put("description", def.description() == null ? "" : def.description());
            entry.put("inputSchema", def.inputSchema() == null ? "" : def.inputSchema());
            tools.add(entry);
        }

        try {
            return objectMapper.writeValueAsString(Map.of("tools", tools));
        } catch (Exception e) {
            log.warn("search_tools: failed to serialize results: {}", e.getMessage());
            return "{\"tools\":[]}";
        }
    }

    private String resolveRequestId(ToolContext toolContext) {
        if (toolContext == null) {
            return null;
        }
        Object requestId = toolContext.getContext().get(ToolEventRegistry.REQUEST_ID);
        return requestId == null ? null : requestId.toString();
    }
}
