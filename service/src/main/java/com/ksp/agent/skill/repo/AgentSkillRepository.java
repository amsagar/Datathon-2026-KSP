package com.ksp.agent.skill.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.skill.entity.AgentSkill;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class AgentSkillRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public AgentSkillRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public String create(AgentSkill s, long now) {
        String sql = sqlQueryLoader.getQuery("SKILL.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, s.getAssistantId());
            ps.setString(2, s.getName());
            ps.setString(3, s.getDescription());
            ps.setString(4, s.getBlobPrefix());
            ps.setBoolean(5, s.isEnabled());
            ps.setLong(6, now);
            ps.setLong(7, now);
            return ps;
        }, keyHolder);
        return String.valueOf(keyHolder.getKeys().get("id"));
    }

    public Optional<AgentSkill> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("SKILL.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    public List<AgentSkill> findByAssistant(String assistantId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("SKILL.FIND_BY_ASSISTANT"), rowMapper(), assistantId);
    }

    public List<AgentSkill> findEnabledByAssistant(String assistantId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("SKILL.FIND_ENABLED_BY_ASSISTANT"), rowMapper(), assistantId);
    }

    public int update(AgentSkill s, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("SKILL.UPDATE"),
                s.getName(), s.getDescription(), s.getBlobPrefix(), s.isEnabled(), now, s.getId());
    }

    public int delete(String id) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("SKILL.DELETE"), id);
    }

    private RowMapper<AgentSkill> rowMapper() {
        return (rs, rowNum) -> new AgentSkill(
                rs.getString("id"),
                rs.getString("assistant_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("blob_prefix"),
                rs.getBoolean("enabled"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
