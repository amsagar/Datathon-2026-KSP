package com.ksp.agent.suggestion.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Storage for empty-screen starter-prompt suggestions. Assistant-level rows have {@code user_id}
 * NULL (seeded or generated from the assistant's own name + system prompt); user-level rows carry a
 * {@code user_id} and are generated from that user's memories + recent sessions. Runs on the primary
 * (app) datasource, alongside the {@code assistant} table.
 */
@Repository
public class PromptSuggestionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sql;

    public PromptSuggestionRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sql) {
        this.jdbcTemplate = jdbcTemplate;
        this.sql = sql;
    }

    public List<String> findAssistantLevel(String assistantId, String lang) {
        return jdbcTemplate.queryForList(
                sql.getQuery("SUGGESTION.FIND_ASSISTANT_LEVEL"), String.class, assistantId, lang);
    }

    public List<String> findForUser(String assistantId, String userId, String lang) {
        return jdbcTemplate.queryForList(
                sql.getQuery("SUGGESTION.FIND_FOR_USER"), String.class, assistantId, userId, lang);
    }

    public int countGenerated(String assistantId, String lang) {
        Integer n = jdbcTemplate.queryForObject(
                sql.getQuery("SUGGESTION.COUNT_GENERATED"), Integer.class, assistantId, lang);
        return n == null ? 0 : n;
    }

    public void deleteAssistantGenerated(String assistantId, String lang) {
        jdbcTemplate.update(sql.getQuery("SUGGESTION.DELETE_ASSISTANT_GENERATED"), assistantId, lang);
    }

    public void deleteUserGenerated(String assistantId, String userId, String lang) {
        jdbcTemplate.update(sql.getQuery("SUGGESTION.DELETE_USER_GENERATED"), assistantId, userId, lang);
    }

    public void insert(String assistantId, String userId, String text, String lang, String source, long now) {
        jdbcTemplate.update(sql.getQuery("SUGGESTION.INSERT"),
                assistantId, userId, text, lang, source, now, now);
    }
}
