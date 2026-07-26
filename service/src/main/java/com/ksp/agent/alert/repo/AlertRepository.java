package com.ksp.agent.alert.repo;

import com.ksp.agent.alert.entity.Alert;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AlertRepository {

    private final JdbcTemplate jdbcTemplate;

    public AlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Opens a new alert for {@code dedupKey}, or is a no-op if a non-resolved alert already
     * exists under that key (the evaluation job re-runs on a schedule and must not spam a fresh
     * duplicate for the same ongoing condition every time it fires).
     */
    public void openIfAbsent(String alertType, Integer districtId, String districtName, String crimeHead,
                             String message, String severity, String dedupKey, long now) {
        jdbcTemplate.update("""
                INSERT INTO alert (alert_type, district_id, district_name, crime_head, message,
                                    severity, status, dedup_key, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?)
                ON CONFLICT (dedup_key) WHERE status <> 'RESOLVED' DO NOTHING
                """, alertType, districtId, districtName, crimeHead, message, severity, dedupKey, now, now);
    }

    public List<Alert> findByStatus(String status, int limit) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query(
                    "SELECT * FROM alert ORDER BY created_at DESC LIMIT ?", rowMapper(), limit);
        }
        return jdbcTemplate.query(
                "SELECT * FROM alert WHERE status = ? ORDER BY created_at DESC LIMIT ?",
                rowMapper(), status, limit);
    }

    public Optional<Alert> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM alert WHERE id = ?", rowMapper(), id)
                .stream().findFirst();
    }

    public int updateStatus(long id, String status, String assignedTo, long now) {
        String tsColumn = switch (status) {
            case "ACKNOWLEDGED" -> "acknowledged_at";
            case "RESOLVED" -> "resolved_at";
            default -> null;
        };
        if (tsColumn == null) {
            return jdbcTemplate.update(
                    "UPDATE alert SET status = ?, assigned_to = COALESCE(?, assigned_to), updated_at = ? WHERE id = ?",
                    status, assignedTo, now, id);
        }
        return jdbcTemplate.update(
                "UPDATE alert SET status = ?, assigned_to = COALESCE(?, assigned_to), updated_at = ?, "
                        + tsColumn + " = ? WHERE id = ?",
                status, assignedTo, now, now, id);
    }

    public int assign(long id, String assignedTo, long now) {
        return jdbcTemplate.update(
                "UPDATE alert SET assigned_to = ?, updated_at = ? WHERE id = ?", assignedTo, now, id);
    }

    private RowMapper<Alert> rowMapper() {
        return (rs, rowNum) -> new Alert(
                rs.getLong("id"),
                rs.getString("alert_type"),
                (Integer) rs.getObject("district_id"),
                rs.getString("district_name"),
                rs.getString("crime_head"),
                rs.getString("message"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getString("assigned_to"),
                rs.getString("dedup_key"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                (Long) rs.getObject("acknowledged_at"),
                (Long) rs.getObject("resolved_at"));
    }
}
