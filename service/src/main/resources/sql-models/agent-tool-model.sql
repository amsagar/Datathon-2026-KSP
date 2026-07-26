CREATE TABLE IF NOT EXISTS tool_auth_profile (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id             UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name                     VARCHAR(200) NOT NULL,
    description              TEXT,
    auth_type                VARCHAR(50) NOT NULL DEFAULT 'none',
    auth_config              TEXT,
    encrypted_client_secret  TEXT,
    token_url                TEXT,
    scopes                   TEXT,
    encrypted_access_token   TEXT,
    token_expires_at         BIGINT,
    created_at               BIGINT NOT NULL,
    updated_at               BIGINT NOT NULL
);

-- Auth profiles are now owned by exactly one assistant (mirrors agent_tool). Previously they were
-- global. Migrate idempotently without dollar-quoted blocks (Spring's script runner splits on ';'
-- and can't parse PL/pgSQL DO blocks): add the owner column as NULLABLE, drop any owner-less rows
-- (only legacy global rows ever have NULL here), then enforce NOT NULL. Every statement is a no-op
-- once migrated, so schema init (mode: always) does NOT wipe the table on restart. On a fresh DB the
-- column already exists NOT NULL from CREATE above, so the DELETE matches nothing.
ALTER TABLE tool_auth_profile ADD COLUMN IF NOT EXISTS assistant_id UUID REFERENCES assistant (id) ON DELETE CASCADE;
DELETE FROM tool_auth_profile WHERE assistant_id IS NULL;
ALTER TABLE tool_auth_profile ALTER COLUMN assistant_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tool_auth_assistant ON tool_auth_profile (assistant_id);

-- Tools are owned by exactly one assistant (mirrors agent_skill). Previously they were global and
-- linked many-to-many via assistant_agent_tool; that join table is dropped below. Existing global
-- rows can't satisfy the NOT NULL owner, so they're cleared (re-import per assistant afterward).
DROP TABLE IF EXISTS assistant_agent_tool;

CREATE TABLE IF NOT EXISTS agent_tool (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id    UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    method          VARCHAR(10) NOT NULL DEFAULT 'GET',
    host            TEXT NOT NULL,
    endpoint        TEXT NOT NULL,
    request_schema  TEXT,
    source_type     VARCHAR(30) NOT NULL DEFAULT 'manual',
    auth_profile_id UUID REFERENCES tool_auth_profile (id) ON DELETE SET NULL,
    auth_type       VARCHAR(50) NOT NULL DEFAULT 'none',
    auth_config     TEXT,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL
);

-- Migrate an older global agent_tool idempotently (same pattern as tool_auth_profile above): add the
-- owner column nullable, drop owner-less legacy rows, enforce NOT NULL. No-op once migrated, so this
-- does NOT wipe tools on every restart.
ALTER TABLE agent_tool ADD COLUMN IF NOT EXISTS assistant_id UUID REFERENCES assistant (id) ON DELETE CASCADE;
DELETE FROM agent_tool WHERE assistant_id IS NULL;
ALTER TABLE agent_tool ALTER COLUMN assistant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_agent_tool_assistant ON agent_tool (assistant_id);
