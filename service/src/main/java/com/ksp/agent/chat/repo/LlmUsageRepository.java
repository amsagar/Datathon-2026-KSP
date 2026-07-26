package com.ksp.agent.chat.repo;

import com.ksp.agent.applicationconfig.configuration.utils.SqlQueryLoader;
import com.ksp.agent.chat.dto.response.UsageBreakdownRowDto;
import com.ksp.agent.chat.dto.response.UsageDailyRowDto;
import com.ksp.agent.chat.dto.response.UsageTotalsDto;
import com.ksp.agent.chat.usage.UsageMetrics.ModelSplitRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class LlmUsageRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SqlQueryLoader sqlQueryLoader;

    public LlmUsageRepository(JdbcTemplate jdbcTemplate, SqlQueryLoader sqlQueryLoader) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlQueryLoader = sqlQueryLoader;
    }

    public void insert(String requestId, String sessionId, String userId, String assistantId,
                       String usageKind, String usageSource, String modelName,
                       int promptTokens, int completionTokens, int totalTokens,
                       Instant createdAt) {
        String sql = sqlQueryLoader.getQuery("USAGE.EVENT.CREATE");
        jdbcTemplate.update(sql, requestId, sessionId, userId, assistantId, usageKind, usageSource,
                modelName, promptTokens, completionTokens, totalTokens,
                java.sql.Timestamp.from(createdAt));
    }

    public UsageTotalsDto summary(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.SUMMARY");
        return jdbcTemplate.queryForObject(sql, totalsRowMapper(),
                ts(from), ts(to), userIdFilter, userIdFilter);
    }

    public List<UsageDailyRowDto> daily(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.DAILY");
        return jdbcTemplate.query(sql, dailyRowMapper(), ts(from), ts(to), userIdFilter, userIdFilter);
    }

    public List<ModelSplitRow> dailyByModel(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.DAILY_BY_MODEL");
        return jdbcTemplate.query(sql, modelSplitRowMapper(), ts(from), ts(to), userIdFilter, userIdFilter);
    }

    public List<UsageBreakdownRowDto> byModel(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.BY_MODEL");
        return jdbcTemplate.query(sql, breakdownRowMapper("model_name"),
                ts(from), ts(to), userIdFilter, userIdFilter);
    }

    /**
     * Real {@code model_name}/{@code usage_kind} split (no "System" display collapse) purely for
     * pricing — {@link #byModel} collapses system-kind rows into a single "System" bucket for
     * display, which isn't a real model that can be priced.
     */
    public List<ModelCostRow> byModelForCost(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.BY_MODEL_FOR_COST");
        return jdbcTemplate.query(sql, modelCostRowMapper(), ts(from), ts(to), userIdFilter, userIdFilter);
    }

    public List<UsageBreakdownRowDto> byUser(Instant from, Instant to) {
        String sql = sqlQueryLoader.getQuery("USAGE.BY_USER");
        return jdbcTemplate.query(sql, breakdownRowMapper("user_id"), ts(from), ts(to));
    }

    /** No {@code userIdFilter} scoping, matching {@link #byUser}'s own (org-wide-only) shape. */
    public List<ModelSplitRow> byUserByModel(Instant from, Instant to) {
        String sql = sqlQueryLoader.getQuery("USAGE.BY_USER_BY_MODEL");
        return jdbcTemplate.query(sql, modelSplitRowMapper(), ts(from), ts(to));
    }

    public List<UsageBreakdownRowDto> byAssistant(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.BY_ASSISTANT");
        return jdbcTemplate.query(sql, breakdownRowMapper("key"), ts(from), ts(to), userIdFilter, userIdFilter);
    }

    public List<ModelSplitRow> byAssistantByModel(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.BY_ASSISTANT_BY_MODEL");
        return jdbcTemplate.query(sql, modelSplitRowMapper(), ts(from), ts(to), userIdFilter, userIdFilter);
    }

    public List<UsageBreakdownRowDto> bySource(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.BY_SOURCE");
        return jdbcTemplate.query(sql, breakdownRowMapper("key"), ts(from), ts(to), userIdFilter, userIdFilter);
    }

    public List<ModelSplitRow> bySourceByModel(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.BY_SOURCE_BY_MODEL");
        return jdbcTemplate.query(sql, modelSplitRowMapper(), ts(from), ts(to), userIdFilter, userIdFilter);
    }

    /** Only hours with at least one event are returned — callers backfill 0-23. */
    public List<UsageBreakdownRowDto> hourly(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.HOURLY");
        return jdbcTemplate.query(sql, hourlyRowMapper(), ts(from), ts(to), userIdFilter, userIdFilter);
    }

    public List<ModelSplitRow> hourlyByModel(Instant from, Instant to, String userIdFilter) {
        String sql = sqlQueryLoader.getQuery("USAGE.HOURLY_BY_MODEL");
        return jdbcTemplate.query(sql, modelSplitRowMapper(), ts(from), ts(to), userIdFilter, userIdFilter);
    }

    private static java.sql.Timestamp ts(Instant instant) {
        return java.sql.Timestamp.from(instant);
    }

    private RowMapper<UsageTotalsDto> totalsRowMapper() {
        return (rs, rowNum) -> new UsageTotalsDto(
                rs.getLong("request_count"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"),
                rs.getLong("total_tokens"),
                0.0
        );
    }

    private RowMapper<UsageDailyRowDto> dailyRowMapper() {
        return (rs, rowNum) -> new UsageDailyRowDto(
                rs.getDate("day").toLocalDate().toString(),
                rs.getLong("request_count"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"),
                rs.getLong("total_tokens"),
                0.0
        );
    }

    private RowMapper<UsageBreakdownRowDto> breakdownRowMapper(String keyColumn) {
        return (rs, rowNum) -> new UsageBreakdownRowDto(
                rs.getString(keyColumn),
                rs.getLong("request_count"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"),
                rs.getLong("total_tokens"),
                0.0
        );
    }

    /** {@code hour} (0-23) as the row key, so the service layer can backfill missing hours. */
    private RowMapper<UsageBreakdownRowDto> hourlyRowMapper() {
        return (rs, rowNum) -> new UsageBreakdownRowDto(
                String.valueOf(rs.getInt("hour")),
                rs.getLong("request_count"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens"),
                rs.getLong("total_tokens"),
                0.0
        );
    }

    private RowMapper<ModelSplitRow> modelSplitRowMapper() {
        return (rs, rowNum) -> new ModelSplitRow(
                rs.getString("bucket_key"),
                rs.getString("model_name"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens")
        );
    }

    private RowMapper<ModelCostRow> modelCostRowMapper() {
        return (rs, rowNum) -> new ModelCostRow(
                rs.getString("model_name"),
                rs.getString("usage_kind"),
                rs.getLong("prompt_tokens"),
                rs.getLong("completion_tokens")
        );
    }

    /**
     * Real {@code model_name} + {@code usage_kind} pair for one slice of usage — used to price a
     * "by model" display row whose key may have collapsed system-kind rows into "System".
     */
    public record ModelCostRow(String modelName, String usageKind, long promptTokens, long completionTokens) {
    }
}
