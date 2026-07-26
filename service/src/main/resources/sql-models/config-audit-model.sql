CREATE TABLE IF NOT EXISTS config_audit_event (
    id            BIGSERIAL PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    resource_id   VARCHAR(64) NOT NULL,
    assistant_id  VARCHAR(64),
    resource_name TEXT,
    action        VARCHAR(24) NOT NULL,
    actor         VARCHAR(320) NOT NULL,
    summary       TEXT,
    created_at    BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_config_audit_event_resource ON config_audit_event (resource_type, resource_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_config_audit_event_actor ON config_audit_event (actor, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_config_audit_event_assistant ON config_audit_event (assistant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_config_audit_event_created ON config_audit_event (created_at DESC);

CREATE TABLE IF NOT EXISTS config_revision (
    id            BIGSERIAL PRIMARY KEY,
    resource_type VARCHAR(32) NOT NULL,
    resource_id   VARCHAR(64) NOT NULL,
    assistant_id  VARCHAR(64),
    version       INTEGER NOT NULL,
    action        VARCHAR(24) NOT NULL,
    actor         VARCHAR(320) NOT NULL,
    snapshot      JSONB NOT NULL,
    content_ref   TEXT,
    summary       TEXT,
    created_at    BIGINT NOT NULL,
    CONSTRAINT uq_config_revision_version UNIQUE (resource_type, resource_id, version)
);

CREATE INDEX IF NOT EXISTS idx_config_revision_resource ON config_revision (resource_type, resource_id, version DESC);

-- Admin-configurable toggle: when true, non-admin roles get read-only access to the
-- config audit feed and revision history (revert always stays admin-only).
CREATE TABLE IF NOT EXISTS audit_access_settings (
    id                     SMALLINT PRIMARY KEY DEFAULT 1,
    non_admin_read_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_by             VARCHAR(320),
    updated_at             BIGINT,
    CONSTRAINT chk_audit_access_singleton CHECK (id = 1)
);

INSERT INTO audit_access_settings (id, non_admin_read_enabled)
VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;
