package com.ksp.agent.chat.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.chat.entity.ChatShare;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ChatShareRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public ChatShareRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    /** Creates the share for a session, or refreshes its snapshot if one already exists (same id). */
    public void upsert(String sessionId, String createdBy, String title, String assistantName,
                       String messagesJson, int messageCount, long now) {
        jdbcTemplate.update(sqlQueryLoader.getQuery("CHAT.SHARE.UPSERT"),
                sessionId, createdBy, title, assistantName, messagesJson, messageCount, now, now);
    }

    public Optional<ChatShare> findBySession(String sessionId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("CHAT.SHARE.FIND_BY_SESSION"), rowMapper(), sessionId)
                .stream().findFirst();
    }

    public Optional<ChatShare> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("CHAT.SHARE.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    public int deleteById(String id) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("CHAT.SHARE.DELETE_BY_ID"), id);
    }

    private RowMapper<ChatShare> rowMapper() {
        return (rs, rowNum) -> new ChatShare(
                rs.getString("id"),
                rs.getString("session_id"),
                rs.getString("created_by"),
                rs.getString("title"),
                rs.getString("assistant_name"),
                rs.getString("messages_json"),
                rs.getInt("message_count"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
