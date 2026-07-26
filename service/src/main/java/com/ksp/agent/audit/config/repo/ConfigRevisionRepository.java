package com.ksp.agent.audit.config.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.audit.config.dto.RevisionSummaryDto;
import com.ksp.agent.audit.config.entity.ConfigRevision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Slf4j
public class ConfigRevisionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;
    private final ObjectMapper objectMapper;

    public ConfigRevisionRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
        this.objectMapper = objectMapper;
    }

    public void insert(String resourceType, String resourceId, String assistantId, int version, String action,
                       String actor, String snapshotJson, String contentRef, String summary, long createdAt) {
        jdbcTemplate.update(sqlQueryLoader.getQuery("CONFIG_REVISION.INSERT"),
                resourceType, resourceId, assistantId, version, action, actor, snapshotJson, contentRef, summary, createdAt);
    }

    public int findMaxVersion(String resourceType, String resourceId) {
        Integer max = jdbcTemplate.queryForObject(sqlQueryLoader.getQuery("CONFIG_REVISION.FIND_MAX_VERSION"),
                Integer.class, resourceType, resourceId);
        return max == null ? 0 : max;
    }

    public List<RevisionSummaryDto> findSummaries(String resourceType, String resourceId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("CONFIG_REVISION.FIND_SUMMARIES"), summaryRowMapper(),
                resourceType, resourceId);
    }

    public Optional<ConfigRevision> findOne(String resourceType, String resourceId, int version) {
        List<ConfigRevision> rows = jdbcTemplate.query(sqlQueryLoader.getQuery("CONFIG_REVISION.FIND_ONE"), rowMapper(),
                resourceType, resourceId, version);
        return rows.stream().findFirst();
    }

    public long count(String resourceType, String resourceId) {
        Long total = jdbcTemplate.queryForObject(sqlQueryLoader.getQuery("CONFIG_REVISION.COUNT"), Long.class,
                resourceType, resourceId);
        return total == null ? 0L : total;
    }

    private RowMapper<RevisionSummaryDto> summaryRowMapper() {
        return (rs, rowNum) -> {
            String contentRef = rs.getString("content_ref");
            return new RevisionSummaryDto(
                    rs.getLong("id"),
                    rs.getInt("version"),
                    rs.getString("action"),
                    rs.getString("actor"),
                    rs.getString("summary"),
                    contentRef != null && !contentRef.isBlank(),
                    rs.getLong("created_at"));
        };
    }

    private RowMapper<ConfigRevision> rowMapper() {
        return (rs, rowNum) -> new ConfigRevision(
                rs.getLong("id"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("assistant_id"),
                rs.getInt("version"),
                rs.getString("action"),
                rs.getString("actor"),
                parseSnapshot(rs.getString("snapshot")),
                rs.getString("content_ref"),
                rs.getString("summary"),
                rs.getLong("created_at"));
    }

    private JsonNode parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Failed to parse config_revision snapshot JSON: {}", e.getMessage());
            return null;
        }
    }
}
