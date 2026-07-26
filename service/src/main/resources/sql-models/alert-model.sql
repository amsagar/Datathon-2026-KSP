-- Phase 4.12: real early-warning alerts (Area 8) — previously a dashboard-only banner
-- (AnalyticsController.earlyWarnings(), never persisted), with no lifecycle. Lives on the APP
-- datasource (like chat_session/audit_log), not the crime DB: alert status/assignment is
-- operational metadata this system owns, not FIR data.
CREATE TABLE IF NOT EXISTS alert (
    id              BIGSERIAL PRIMARY KEY,
    alert_type      VARCHAR(40) NOT NULL,   -- CRIME_SPIKE | REPEAT_OFFENDER_SURGE | GANG_ACTIVITY
    district_id     INT,
    district_name   VARCHAR(100),
    crime_head      VARCHAR(150),
    message         TEXT NOT NULL,
    severity        VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',  -- LOW | MEDIUM | HIGH
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',    -- OPEN | ACKNOWLEDGED | RESOLVED
    assigned_to     VARCHAR(320),
    -- Natural key for one alert per (type, district, crime_head) while it's not yet resolved —
    -- lets the evaluation job re-run on a schedule without spamming a duplicate for the same
    -- ongoing condition every time it fires.
    dedup_key       VARCHAR(200) NOT NULL,
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL,
    acknowledged_at BIGINT,
    resolved_at     BIGINT
);
CREATE INDEX IF NOT EXISTS idx_alert_status ON alert (status, created_at DESC);
-- Partial unique index: only one non-resolved alert per dedup_key at a time; once resolved, a
-- fresh recurrence of the same condition can open a new alert under the same key.
CREATE UNIQUE INDEX IF NOT EXISTS idx_alert_dedup_open ON alert (dedup_key) WHERE status <> 'RESOLVED';
