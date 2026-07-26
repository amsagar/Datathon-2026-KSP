package com.ksp.agent.mcp.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.mcp.entity.McpServerTool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class McpServerToolRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public McpServerToolRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    /** Insert the discovered tool, or refresh its description/schema if already known (enabled flag preserved). */
    public int upsert(String serverId, String name, String description, String inputSchema, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("MCP.TOOL.UPSERT"),
                serverId, name, description, inputSchema, now, now);
    }

    public Optional<McpServerTool> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("MCP.TOOL.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    public List<McpServerTool> findByServer(String serverId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("MCP.TOOL.FIND_BY_SERVER"), rowMapper(), serverId);
    }

    public List<McpServerTool> findEnabledByServer(String serverId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("MCP.TOOL.FIND_ENABLED_BY_SERVER"), rowMapper(), serverId);
    }

    public int setEnabled(String id, boolean enabled, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("MCP.TOOL.SET_ENABLED"), enabled, now, id);
    }

    public int deleteByServer(String serverId) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("MCP.TOOL.DELETE_BY_SERVER"), serverId);
    }

    private RowMapper<McpServerTool> rowMapper() {
        return (rs, rowNum) -> new McpServerTool(
                rs.getString("id"),
                rs.getString("server_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("input_schema"),
                rs.getBoolean("enabled"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
