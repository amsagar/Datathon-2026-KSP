package com.ksp.agent.skill.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.skill.entity.AgentSkillRevision;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.UUID;

@Repository
public class AgentSkillRevisionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public AgentSkillRevisionRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public String create(AgentSkillRevision revision, long now) {
        String sql = sqlQueryLoader.getQuery("SKILL_REVISION.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, revision.getSkillId());
            ps.setString(2, revision.getAssistantId());
            ps.setString(3, revision.getFilePath());
            ps.setString(4, revision.getSummary());
            ps.setString(5, revision.getFeedbackQuote());
            ps.setBoolean(6, revision.isApproved());
            ps.setString(7, revision.getDecidedBy());
            ps.setString(8, revision.getSessionId());
            ps.setString(9, revision.getRequestId());
            ps.setLong(10, now);
            return ps;
        }, keyHolder);
        Object id = keyHolder.getKeys().get("id");
        return id != null ? String.valueOf(id) : UUID.randomUUID().toString();
    }
}
