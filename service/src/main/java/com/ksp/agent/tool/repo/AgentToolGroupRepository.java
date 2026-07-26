package com.ksp.agent.tool.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.tool.entity.AgentToolGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class AgentToolGroupRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public AgentToolGroupRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public String create(AgentToolGroup group, long now) {
        String sql = sqlQueryLoader.getQuery("TOOL_GROUP.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, group.getAssistantId());
            ps.setString(2, group.getName());
            ps.setString(3, group.getDescription());
            ps.setString(4, group.getSourceType());
            ps.setBoolean(5, group.isEnabled());
            ps.setLong(6, now);
            ps.setLong(7, now);
            return ps;
        }, keyHolder);
        return String.valueOf(keyHolder.getKeys().get("id"));
    }

    public Optional<AgentToolGroup> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("TOOL_GROUP.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    public List<AgentToolGroup> findByAssistant(String assistantId) {
        return jdbcTemplate.query(
                sqlQueryLoader.getQuery("TOOL_GROUP.FIND_BY_ASSISTANT"), rowMapper(), assistantId);
    }

    public int update(AgentToolGroup group, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("TOOL_GROUP.UPDATE"),
                group.getName(), group.getDescription(), group.isEnabled(), now, group.getId());
    }

    public int delete(String id) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("TOOL_GROUP.DELETE"), id);
    }

    private RowMapper<AgentToolGroup> rowMapper() {
        return (rs, rowNum) -> new AgentToolGroup(
                rs.getString("id"),
                rs.getString("assistant_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("source_type"),
                rs.getBoolean("enabled"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
