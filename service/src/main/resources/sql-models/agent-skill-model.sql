CREATE TABLE IF NOT EXISTS agent_skill (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    blob_prefix  TEXT NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   BIGINT NOT NULL,
    updated_at   BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_skill_assistant ON agent_skill (assistant_id);

CREATE TABLE IF NOT EXISTS agent_skill_revision (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id     UUID NOT NULL REFERENCES agent_skill (id) ON DELETE CASCADE,
    assistant_id UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    file_path    TEXT NOT NULL,
    summary      TEXT,
    feedback_quote TEXT,
    approved     BOOLEAN NOT NULL,
    decided_by   TEXT NOT NULL,
    session_id   UUID,
    request_id   TEXT,
    created_at   BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_skill_revision_skill ON agent_skill_revision (skill_id);
