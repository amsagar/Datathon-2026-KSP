package com.ksp.agent.assistant.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.assistant.entity.Assistant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

@Repository
public class AssistantRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public AssistantRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public String create(String name, String systemPrompt, String builtinTools, String platformSkills, long now) {
        String sql = sqlQueryLoader.getQuery("ASSISTANT.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, name);
            ps.setString(2, systemPrompt);
            ps.setString(3, builtinTools);
            ps.setString(4, platformSkills);
            ps.setLong(5, now);
            ps.setLong(6, now);
            return ps;
        }, keyHolder);
        return String.valueOf(keyHolder.getKeys().get("id"));
    }

    public List<Assistant> findAll() {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("ASSISTANT.FIND_ALL"), rowMapper());
    }

    public Optional<Assistant> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("ASSISTANT.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    public Optional<Assistant> findByName(String name) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("ASSISTANT.FIND_BY_NAME"), rowMapper(), name)
                .stream().findFirst();
    }

    public int update(String id, String name, String systemPrompt, String builtinTools,
                      String platformSkills, long now) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("ASSISTANT.UPDATE"),
                name, systemPrompt, builtinTools, platformSkills, now, id);
    }

    public int delete(String id) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("ASSISTANT.DELETE"), id);
    }

    private RowMapper<Assistant> rowMapper() {
        return (rs, rowNum) -> new Assistant(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("system_prompt"),
                rs.getString("builtin_tools"),
                rs.getString("platform_skills"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
