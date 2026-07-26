package com.ksp.agent.audit.config.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.audit.config.entity.ConfigAuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ConfigAuditEventRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public ConfigAuditEventRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public void insert(String resourceType, String resourceId, String assistantId, String resourceName,
                        String action, String actor, String summary, long createdAt) {
        jdbcTemplate.update(sqlQueryLoader.getQuery("CONFIG_AUDIT_EVENT.INSERT"),
                resourceType, resourceId, assistantId, resourceName, action, actor, summary, createdAt);
    }

    public List<ConfigAuditEvent> findFeed(String resourceType, String actor, String resourceId,
                                           Long from, Long to, int limit, int offset) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("CONFIG_AUDIT_EVENT.FIND_FEED"), rowMapper(),
                resourceType, resourceType, actor, actor, resourceId, resourceId, from, from, to, to, limit, offset);
    }

    public long countFeed(String resourceType, String actor, String resourceId, Long from, Long to) {
        Long total = jdbcTemplate.queryForObject(sqlQueryLoader.getQuery("CONFIG_AUDIT_EVENT.COUNT_FEED"), Long.class,
                resourceType, resourceType, actor, actor, resourceId, resourceId, from, from, to, to);
        return total == null ? 0L : total;
    }

    private RowMapper<ConfigAuditEvent> rowMapper() {
        return (rs, rowNum) -> new ConfigAuditEvent(
                rs.getLong("id"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("assistant_id"),
                rs.getString("resource_name"),
                rs.getString("action"),
                rs.getString("actor"),
                rs.getString("summary"),
                rs.getLong("created_at"));
    }
}
