package com.ksp.agent.tool.auth.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.tool.auth.entity.ToolAuthProfile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class ToolAuthProfileRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public ToolAuthProfileRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public String create(ToolAuthProfile p, long now) {
        String sql = sqlQueryLoader.getQuery("TOOL_AUTH.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, p.getAssistantId());
            ps.setString(2, p.getName());
            ps.setString(3, p.getDescription());
            ps.setString(4, p.getAuthType());
            ps.setString(5, p.getAuthConfig());
            ps.setString(6, p.getEncryptedClientSecret());
            ps.setString(7, p.getTokenUrl());
            ps.setString(8, p.getScopes());
            ps.setLong(9, now);
            ps.setLong(10, now);
            return ps;
        }, keyHolder);
        return String.valueOf(keyHolder.getKeys().get("id"));
    }

    public List<ToolAuthProfile> findByAssistant(String assistantId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("TOOL_AUTH.FIND_BY_ASSISTANT"), rowMapper(), assistantId);
    }

    public Optional<ToolAuthProfile> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("TOOL_AUTH.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    public int update(ToolAuthProfile p, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("TOOL_AUTH.UPDATE"),
                p.getName(), p.getDescription(), p.getAuthType(), p.getAuthConfig(),
                p.getEncryptedClientSecret(), p.getTokenUrl(), p.getScopes(), now, p.getId());
    }

    public int updateToken(String id, String encryptedAccessToken, Long expiresAt, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("TOOL_AUTH.UPDATE_TOKEN"),
                encryptedAccessToken, expiresAt, now, id);
    }

    public int delete(String id) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("TOOL_AUTH.DELETE"), id);
    }

    private RowMapper<ToolAuthProfile> rowMapper() {
        return (rs, rowNum) -> new ToolAuthProfile(
                rs.getString("id"),
                rs.getString("assistant_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("auth_type"),
                rs.getString("auth_config"),
                rs.getString("encrypted_client_secret"),
                rs.getString("token_url"),
                rs.getString("scopes"),
                rs.getString("encrypted_access_token"),
                getNullableLong(rs, "token_expires_at"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }

    private static Long getNullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
