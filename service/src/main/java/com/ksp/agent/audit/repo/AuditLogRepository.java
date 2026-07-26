package com.ksp.agent.audit.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.audit.entity.AuditLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public AuditLogRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public void insert(String actor, String action, String target, String details, long now) {
        jdbcTemplate.update(sqlQueryLoader.getQuery("AUDIT.CREATE"), actor, action, target, details, now);
    }

    public List<AuditLog> findRecent(String search, String action, int limit, int offset) {
        String like = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
        String actionFilter = action == null || action.isBlank() ? null : action.trim();
        return jdbcTemplate.query(sqlQueryLoader.getQuery("AUDIT.FIND_RECENT"), rowMapper(),
                like, like, like, like, actionFilter, actionFilter, limit, offset);
    }

    public long count(String search, String action) {
        String like = search == null || search.isBlank() ? null : "%" + search.trim().toLowerCase() + "%";
        String actionFilter = action == null || action.isBlank() ? null : action.trim();
        Long total = jdbcTemplate.queryForObject(sqlQueryLoader.getQuery("AUDIT.COUNT"), Long.class,
                like, like, like, like, actionFilter, actionFilter);
        return total == null ? 0L : total;
    }

    private RowMapper<AuditLog> rowMapper() {
        return (rs, rowNum) -> new AuditLog(
                rs.getString("id"),
                rs.getString("actor"),
                rs.getString("action"),
                rs.getString("target"),
                rs.getString("details"),
                rs.getLong("created_at"));
    }
}
