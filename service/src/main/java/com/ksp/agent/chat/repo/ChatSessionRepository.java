package com.ksp.agent.chat.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.chat.entity.ChatSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class ChatSessionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public ChatSessionRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public String create(String title, String assistantId, String userId, boolean temporary, long now) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, title);
            ps.setString(2, assistantId);
            ps.setString(3, userId);
            ps.setBoolean(4, temporary);
            ps.setLong(5, now);
            ps.setLong(6, now);
            return ps;
        }, keyHolder);
        Object id = keyHolder.getKeys().get("id");
        return String.valueOf(id);
    }

    public List<ChatSession> findByArchived(boolean archived, String userId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.FIND_BY_ARCHIVED");
        return jdbcTemplate.query(sql, sessionRowMapper(), archived, userId);
    }

    public Optional<ChatSession> findById(String id, String userId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.FIND_BY_ID");
        return jdbcTemplate.query(sql, sessionRowMapper(), id, userId).stream().findFirst();
    }

    public int updateTitle(String id, String title, long now, String userId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.UPDATE_TITLE");
        return jdbcTemplate.update(sql, title, now, id, userId);
    }

    public int updateArchived(String id, boolean archived, long now, String userId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.UPDATE_ARCHIVED");
        return jdbcTemplate.update(sql, archived, now, id, userId);
    }

    public int touch(String id, long now, String userId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.TOUCH");
        return jdbcTemplate.update(sql, now, id, userId);
    }

    public int updateStyle(String id, String styleId, long now, String userId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.UPDATE_STYLE");
        return jdbcTemplate.update(sql, styleId, now, id, userId);
    }

    public int updateProvider(String id, String providerId, long now, String userId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.UPDATE_PROVIDER");
        return jdbcTemplate.update(sql, providerId, now, id, userId);
    }

    public int delete(String id, String userId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.DELETE");
        return jdbcTemplate.update(sql, id, userId);
    }

    /** Delete by id without the per-user filter — used by the temporary-chat retention purge job. */
    public int deleteById(String id) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.DELETE_BY_ID");
        return jdbcTemplate.update(sql, id);
    }

    /** Ids of temporary sessions last touched before {@code cutoffEpochSeconds} (purge candidates). */
    public List<String> findExpiredTemporary(long cutoffEpochSeconds) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.FIND_EXPIRED_TEMPORARY");
        return jdbcTemplate.queryForList(sql, String.class, cutoffEpochSeconds);
    }

    /** Owner (UPN) and assistant of a session, without the per-user filter. */
    public record SessionOwner(String userId, String assistantId, boolean temporary) {}

    /** One row of a cross-user session listing — supervisor/admin oversight only. */
    public record SessionSummary(String id, String userId, String title, Long updatedAt,
                                 boolean archived, boolean temporary) {}

    /**
     * Most-recently-updated sessions across ALL users, without the per-user filter — backs the
     * supervisor/admin "review any user's chat + tool trail" read path (Phase 4.4). Every mutating
     * repository method above stays owner-scoped; this is read-only oversight, no different in
     * kind from an admin log viewer.
     */
    public List<SessionSummary> findMostRecentAcrossUsers(int limit) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.FIND_MOST_RECENT_ACROSS_USERS");
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SessionSummary(
                        rs.getString("id"), rs.getString("user_id"), rs.getString("title"),
                        rs.getLong("updated_at"), rs.getBoolean("archived"), rs.getBoolean("temporary")),
                limit);
    }

    /**
     * Resolve a session's owner and assistant without requiring a SecurityContext. Used by the async
     * consolidation path (off the request thread) to attribute extracted facts to the right user.
     */
    public Optional<SessionOwner> findOwner(String sessionId) {
        String sql = sqlQueryLoader.getQuery("CHAT.SESSION.FIND_OWNER");
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new SessionOwner(
                        rs.getString("user_id"), rs.getString("assistant_id"), rs.getBoolean("temporary")),
                sessionId).stream().findFirst();
    }

    private RowMapper<ChatSession> sessionRowMapper() {
        return (rs, rowNum) -> new ChatSession(
                rs.getString("id"),
                rs.getString("title"),
                rs.getBoolean("archived"),
                rs.getString("assistant_id"),
                rs.getString("style_id"),
                rs.getString("provider_id"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                rs.getBoolean("temporary")
        );
    }
}
