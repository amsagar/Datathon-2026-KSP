package com.ksp.agent.tool.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.tool.entity.AgentTool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AgentToolRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public AgentToolRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public String create(AgentTool t, long now) {
        String sql = sqlQueryLoader.getQuery("TOOL.CREATE");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, t.getAssistantId());
            setNullableString(ps, 2, t.getGroupId());
            ps.setString(3, t.getName());
            ps.setString(4, t.getDescription());
            ps.setString(5, t.getMethod());
            ps.setString(6, t.getHost());
            ps.setString(7, t.getEndpoint());
            ps.setString(8, t.getRequestSchema());
            ps.setString(9, t.getSourceType());
            setNullableString(ps, 10, t.getAuthProfileId());
            ps.setString(11, t.getAuthType());
            ps.setString(12, t.getAuthConfig());
            ps.setBoolean(13, t.isEnabled());
            ps.setLong(14, now);
            ps.setLong(15, now);
            return ps;
        }, keyHolder);
        return String.valueOf(keyHolder.getKeys().get("id"));
    }

    public Optional<AgentTool> findById(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("TOOL.FIND_BY_ID"), rowMapper(), id)
                .stream().findFirst();
    }

    /** All of an assistant's tools (enabled and disabled), for management UI. */
    public List<AgentTool> findByAssistant(String assistantId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("TOOL.FIND_BY_ASSISTANT"), rowMapper(), assistantId);
    }

    /** Only an assistant's enabled tools, for runtime callback assembly. */
    public List<AgentTool> findEnabledByAssistant(String assistantId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("TOOL.FIND_ENABLED_BY_ASSISTANT"), rowMapper(), assistantId);
    }

    public int update(AgentTool t, long now) {
        return jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    sqlQueryLoader.getQuery("TOOL.UPDATE"));
            ps.setString(1, t.getName());
            ps.setString(2, t.getDescription());
            ps.setString(3, t.getMethod());
            ps.setString(4, t.getHost());
            ps.setString(5, t.getEndpoint());
            ps.setString(6, t.getRequestSchema());
            setNullableString(ps, 7, t.getAuthProfileId());
            ps.setString(8, t.getAuthType());
            ps.setString(9, t.getAuthConfig());
            ps.setBoolean(10, t.isEnabled());
            setNullableString(ps, 11, t.getGroupId());
            ps.setLong(12, now);
            ps.setString(13, t.getId());
            return ps;
        });
    }

    public int delete(String id) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("TOOL.DELETE"), id);
    }

    /** Import tools created before tool groups existed (no group_id yet). */
    public List<AgentTool> findUngroupedImports() {
        return jdbcTemplate.query(
                sqlQueryLoader.getQuery("TOOL.FIND_UNGROUPED_IMPORTS"), rowMapper());
    }

    /** Current stored hash of the name+description used to build the embedding, or null. */
    public String findEmbeddingHash(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("TOOL.FIND_EMBEDDING_HASH"),
                        rs -> rs.next() ? rs.getString(1) : null, id);
    }

    /** Stores the pgvector embedding literal (e.g. "[0.1,0.2,...]") plus its source hash. */
    public int updateEmbedding(String id, String vectorLiteral, String hash) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("TOOL.UPDATE_EMBEDDING"),
                vectorLiteral, hash, id);
    }

    /** Tools that have no embedding yet (id, name, description), for startup backfill. */
    public List<EmbeddingCandidate> findMissingEmbedding() {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("TOOL.FIND_MISSING_EMBEDDING"),
                (rs, rowNum) -> new EmbeddingCandidate(
                        rs.getString("id"), rs.getString("name"), rs.getString("description")));
    }

    /**
     * Cosine similarity (1 - distance) of each of an assistant's enabled, embedded tools against
     * the supplied query vector, keyed by the raw {@code agent_tool.name}. Computed DB-side via
     * pgvector's {@code <=>} operator.
     */
    public Map<String, Double> similarityScores(String assistantId, String vectorLiteral) {
        Map<String, Double> scores = new java.util.HashMap<>();
        jdbcTemplate.query(sqlQueryLoader.getQuery("TOOL.SIMILARITY_BY_ASSISTANT"),
                rs -> { scores.put(rs.getString("name"), rs.getDouble("score")); },
                vectorLiteral, assistantId);
        return scores;
    }

    public record EmbeddingCandidate(String id, String name, String description) {}

    private static void setNullableString(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.OTHER);
        } else {
            ps.setString(index, value);
        }
    }

    private RowMapper<AgentTool> rowMapper() {
        return (rs, rowNum) -> new AgentTool(
                rs.getString("id"),
                rs.getString("assistant_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("method"),
                rs.getString("host"),
                rs.getString("endpoint"),
                rs.getString("request_schema"),
                rs.getString("source_type"),
                rs.getString("auth_profile_id"),
                rs.getString("auth_type"),
                rs.getString("auth_config"),
                rs.getString("group_id"),
                rs.getBoolean("enabled"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
