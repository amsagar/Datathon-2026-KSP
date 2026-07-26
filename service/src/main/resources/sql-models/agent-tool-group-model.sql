CREATE TABLE IF NOT EXISTS agent_tool_group (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id    UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    source_type     VARCHAR(30) NOT NULL DEFAULT 'manual',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_tool_group_assistant ON agent_tool_group (assistant_id);

ALTER TABLE agent_tool ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES agent_tool_group (id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_agent_tool_group ON agent_tool (group_id);
