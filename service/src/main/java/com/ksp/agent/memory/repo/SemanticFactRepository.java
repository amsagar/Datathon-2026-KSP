package com.ksp.agent.memory.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.memory.entity.SemanticFact;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;

/**
 * Persistence for {@link SemanticFact}, following the {@code AgentToolRepository} idiom
 * ({@link JdbcTemplate} + named queries from {@link SqlQueryLoader}). Recall ranks facts by pgvector
 * cosine similarity ({@code <=>}) after a cheap scope pre-filter.
 */
@Repository
public class SemanticFactRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public SemanticFactRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    /** A fact ranked by similarity to a query, returned from {@link #recall}. */
    public record ScoredFact(String id, String subject, String predicate, String object,
                             double confidence, double importance, double score) {
        public String render() {
            return subject + " " + predicate + " " + object;
        }
    }

    /** The text fields needed to (re-)embed a fact. */
    public record FactText(String id, String subject, String predicate, String object) {
        public String embedText() {
            return (subject == null ? "" : subject) + " "
                    + (predicate == null ? "" : predicate) + " "
                    + (object == null ? "" : object);
        }
    }

    /** Insert a new fact; returns the generated id. */
    public String insert(SemanticFact f, long now) {
        String sql = sqlQueryLoader.getQuery("FACT.INSERT");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "id" });
            ps.setString(1, f.getUserId());
            ps.setString(2, f.getAssistantId());
            ps.setString(3, f.getSessionId());
            ps.setString(4, f.getSubject());
            ps.setString(5, f.getPredicate());
            ps.setString(6, f.getObject());
            ps.setFloat(7, f.getConfidence());
            ps.setFloat(8, f.getImportance());
            ps.setLong(9, now);
            ps.setLong(10, now);
            return ps;
        }, keyHolder);
        Object id = keyHolder.getKeys() != null ? keyHolder.getKeys().get("id") : null;
        return id == null ? null : String.valueOf(id);
    }

    /** Soft-retract any active fact in the same scope with the same subject+predicate (case-insensitive). */
    public int supersede(String userId, String assistantId, String subject, String predicate) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("FACT.SUPERSEDE"),
                userId, assistantId, subject, predicate);
    }

    /**
     * Top-K active facts in scope, ranked by cosine similarity to the query vector. Scope is the
     * given user (plus assistant-shared facts) and the given assistant (plus user-global facts).
     */
    public List<ScoredFact> recall(String userId, String assistantId, String queryVectorLiteral,
                                   int topK, double minConfidence) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("FACT.RECALL"),
                (rs, n) -> new ScoredFact(
                        rs.getString("id"),
                        rs.getString("subject"),
                        rs.getString("predicate"),
                        rs.getString("object"),
                        rs.getDouble("confidence"),
                        rs.getDouble("importance"),
                        rs.getDouble("score")),
                queryVectorLiteral, userId, assistantId, minConfidence, topK);
    }

    /** Reinforce importance and bump last-accessed for the recalled facts (counteracts decay). */
    public int reinforce(List<String> ids, double boost, long now) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        String arrayLiteral = "{" + String.join(",", ids) + "}";
        return jdbcTemplate.update(sqlQueryLoader.getQuery("FACT.REINFORCE"), boost, now, arrayLiteral);
    }

    /** Current stored hash of the embedded text, or null. */
    public String findEmbeddingHash(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("FACT.FIND_EMBEDDING_HASH"),
                rs -> rs.next() ? rs.getString(1) : null, id);
    }

    /** Store the pgvector embedding literal (e.g. "[0.1,0.2,...]") plus its source hash. */
    public int updateEmbedding(String id, String vectorLiteral, String hash) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("FACT.UPDATE_EMBEDDING"),
                vectorLiteral, hash, id);
    }

    /** The text fields of a single fact (for re-embedding a freshly inserted one). */
    public FactText findText(String id) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("FACT.FIND_TEXT_BY_ID"),
                rs -> rs.next()
                        ? new FactText(id, rs.getString("subject"), rs.getString("predicate"),
                                rs.getString("object"))
                        : null,
                id);
    }

    /** Active facts that have no embedding yet, for startup backfill. */
    public List<FactText> findMissingEmbedding() {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("FACT.FIND_MISSING_EMBEDDING"),
                (rs, n) -> new FactText(rs.getString("id"), rs.getString("subject"),
                        rs.getString("predicate"), rs.getString("object")));
    }

    /** Active facts owned by a user (most-recently-used first), for the manage-memories view. */
    public List<SemanticFact> listByUser(String userId) {
        return jdbcTemplate.query(sqlQueryLoader.getQuery("FACT.LIST_BY_USER"),
                (rs, n) -> SemanticFact.builder()
                        .id(rs.getString("id"))
                        .userId(userId)
                        .assistantId(rs.getString("assistant_id"))
                        .sessionId(rs.getString("session_id"))
                        .subject(rs.getString("subject"))
                        .predicate(rs.getString("predicate"))
                        .object(rs.getString("object"))
                        .confidence(rs.getFloat("confidence"))
                        .importance(rs.getFloat("importance"))
                        .superseded(rs.getBoolean("superseded"))
                        .createdAt(rs.getLong("created_at"))
                        .lastAccessedAt(rs.getLong("last_accessed_at"))
                        .build(),
                userId);
    }

    /** Delete a single fact, scoped to its owner so a user can only forget their own memories. */
    public int deleteForUser(String id, String userId) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("FACT.DELETE_BY_ID_FOR_USER"), id, userId);
    }

    /** Delete all of a user's facts ("forget everything about me"). */
    public int deleteAllForUser(String userId) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("FACT.DELETE_ALL_BY_USER"), userId);
    }

    /** Apply exponential decay to importance based on time since last access. */
    public int decay(double lambda, long now) {
        // importance *= exp(-lambda * daysSinceLastAccess); pass -lambda so the SQL stays a plain product.
        return jdbcTemplate.update(sqlQueryLoader.getQuery("FACT.DECAY"), -lambda, now);
    }

    /** Delete faded (below-threshold) and superseded facts. */
    public int prune(double threshold) {
        return jdbcTemplate.update(sqlQueryLoader.getQuery("FACT.PRUNE"), threshold);
    }
}
