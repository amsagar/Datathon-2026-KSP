package com.ksp.agent.mcp.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.mcp.entity.McpServer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class McpServerRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public McpServerRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public String create(McpServer s, long now) {
        String sql = sqlQueryLoader.getQuery("MCP.SERVER.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, s.getAssistantId());
            ps.setString(2, s.getName());
            ps.setString(3, s.getDescription());
            ps.setString(4, s.getTransport());
            ps.setString(5, s.getUrl());
            ps.setString(6, s.getSseEndpoint());
            ps.setString(7, s.getAuthType());
            ps.setString(8, s.getAuthConfig());
            ps.setString(9, s.getEncryptedSecret());
            ps.setBoolean(10, s.isEnabled());
            ps.setLong(11, now);
            ps.setLong(12, now);
            return ps;
        }, keyHolder);
        return String.valueOf(keyHolder.getKeys().get("id"));
    }

    public Optional<McpServer> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("MCP.SERVER.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    /** All of an assistant's MCP servers (enabled and disabled), for the management UI. */
    public List<McpServer> findByAssistant(String assistantId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("MCP.SERVER.FIND_BY_ASSISTANT"), rowMapper(), assistantId);
    }

    /** Only an assistant's enabled MCP servers, for runtime callback assembly. */
    public List<McpServer> findEnabledByAssistant(String assistantId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("MCP.SERVER.FIND_ENABLED_BY_ASSISTANT"), rowMapper(), assistantId);
    }

    public int update(McpServer s, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("MCP.SERVER.UPDATE"),
                s.getName(), s.getDescription(), s.getTransport(), s.getUrl(), s.getSseEndpoint(),
                s.getAuthType(), s.getAuthConfig(), s.getEncryptedSecret(), s.isEnabled(), now, s.getId());
    }

    public int updateStatus(String id, String status, String detail, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("MCP.SERVER.UPDATE_STATUS"),
                status, detail, now, id);
    }

    public int updateToken(String id, String encryptedAccessToken, long expiresAt, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("MCP.SERVER.UPDATE_TOKEN"),
                encryptedAccessToken, expiresAt, now, id);
    }

    public int delete(String id) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("MCP.SERVER.DELETE"), id);
    }

    private RowMapper<McpServer> rowMapper() {
        return (rs, rowNum) -> new McpServer(
                rs.getString("id"),
                rs.getString("assistant_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("transport"),
                rs.getString("url"),
                rs.getString("sse_endpoint"),
                rs.getString("auth_type"),
                rs.getString("auth_config"),
                rs.getString("encrypted_secret"),
                rs.getString("encrypted_access_token"),
                (Long) rs.getObject("token_expires_at"),
                rs.getBoolean("enabled"),
                rs.getString("status"),
                rs.getString("status_detail"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
