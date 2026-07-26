package com.ksp.agent.chat.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.chat.entity.ChatSessionSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ChatSessionSummaryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public ChatSessionSummaryRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public Optional<ChatSessionSummary> findBySession(String sessionId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION_SUMMARY.FIND_BY_SESSION");
        return jdbcTemplate.query(sql, summaryRowMapper(), sessionId).stream().findFirst();
    }

    public void upsert(String sessionId, String summary, int summarizedThroughCount, long now) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION_SUMMARY.UPSERT");
        jdbcTemplate.update(sql, sessionId, summary, summarizedThroughCount, now);
    }

    public int deleteBySession(String sessionId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION_SUMMARY.DELETE");
        return jdbcTemplate.update(sql, sessionId);
    }

    private RowMapper<ChatSessionSummary> summaryRowMapper() {
        return (rs, rowNum) -> new ChatSessionSummary(
                rs.getString("session_id"),
                rs.getString("summary"),
                rs.getInt("summarized_through_count"),
                rs.getLong("updated_at")
        );
    }
}
