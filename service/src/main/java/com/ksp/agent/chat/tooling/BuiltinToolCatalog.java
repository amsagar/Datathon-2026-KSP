package com.ksp.agent.chat.tooling;

import com.ksp.agent.assistant.dto.response.BuiltinToolDto;
import com.ksp.agent.crime.tooling.CrimeAnalyticsTools;
import com.ksp.agent.crime.tooling.CrimeDatabaseTools;
import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog of code-defined ("built-in") tools, keyed by a stable string so assistants
 * can enable a subset. Each entry resolves to one or more Spring AI {@link ToolCallback}s.
 */
@Component
public class BuiltinToolCatalog {

    /** Catalog key; tool instance is created per chat turn in {@link AskUserQuestionToolFactory}. */
    public static final String ASK_USER_QUESTION_KEY = "ask_user_question";

    private record Entry(String label, Object toolObject) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public BuiltinToolCatalog(CrimeDatabaseTools crimeDatabaseTools, CrimeAnalyticsTools crimeAnalyticsTools) {
        entries.put("file_system", new Entry("File system (read/write/edit files)", FileSystemTools.builder().build()));
        entries.put("grep", new Entry("Grep (search file contents)", GrepTool.builder().build()));
        entries.put("glob", new Entry("Glob (match files by pattern)", GlobTool.builder().build()));
        entries.put("shell", new Entry("Shell (run commands)", ShellTools.builder().build()));
        entries.put("crime_db", new Entry(
                "Crime FIR database (schema, read-only SQL: crime statistics, cases, trends, offenders)",
                crimeDatabaseTools));
        entries.put("crime_analytics", new Entry(
                "Crime analytics (forecasting, organized-group detection, seasonality, demographics, "
                        + "similar cases, financial money-trail)",
                crimeAnalyticsTools));
        entries.put(ASK_USER_QUESTION_KEY,
                new Entry("Ask user (clarify before acting)", null));
    }

    public List<BuiltinToolDto> catalog() {
        List<BuiltinToolDto> list = new ArrayList<>();
        entries.forEach((key, entry) -> list.add(new BuiltinToolDto(key, entry.label())));
        return list;
    }

    public List<String> keys() {
        return new ArrayList<>(entries.keySet());
    }

    public List<ToolCallback> callbacksFor(Collection<String> keys) {
        List<ToolCallback> callbacks = new ArrayList<>();
        if (keys == null) {
            return callbacks;
        }
        for (String key : keys) {
            Entry entry = entries.get(key);
            if (entry != null && entry.toolObject() != null) {
                callbacks.addAll(List.of(ToolCallbacks.from(entry.toolObject())));
            }
        }
        return callbacks;
    }
}
