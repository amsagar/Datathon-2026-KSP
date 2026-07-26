-- MCP (Model Context Protocol) servers are owned by exactly one assistant (mirrors agent_tool /
-- agent_skill). Each server is a remote HTTP endpoint (Streamable HTTP or SSE). Secrets are stored
-- encrypted by EncryptionService. Discovered tools live in mcp_server_tool with a per-tool enabled
-- flag so the assistant can expose a subset to the model.
CREATE TABLE IF NOT EXISTS mcp_server (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id  UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    transport     VARCHAR(40)  NOT NULL DEFAULT 'streamable_http', -- 'streamable_http' | 'sse'
    url           TEXT NOT NULL,                                    -- base URL / endpoint
    sse_endpoint  TEXT,                                             -- optional SSE path override
    auth_type     VARCHAR(40)  NOT NULL DEFAULT 'none',
    auth_config   TEXT,                                             -- JSON: non-secret fields
    encrypted_secret        TEXT,                                   -- encrypted apiKey/token/password/clientSecret
    encrypted_access_token  TEXT,                                   -- cached OAuth access token (encrypted)
    token_expires_at        BIGINT,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,                    -- server-level on/off
    status        VARCHAR(40),                                      -- last connect status: 'connected' | 'error'
    status_detail TEXT,                                             -- last error message
    created_at    BIGINT NOT NULL,
    updated_at    BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mcp_server_assistant ON mcp_server (assistant_id);

CREATE TABLE IF NOT EXISTS mcp_server_tool (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id     UUID NOT NULL REFERENCES mcp_server (id) ON DELETE CASCADE,
    name          VARCHAR(300) NOT NULL,
    description   TEXT,
    input_schema  TEXT,                                             -- JSON schema as discovered
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    BIGINT NOT NULL,
    updated_at    BIGINT NOT NULL,
    UNIQUE (server_id, name)
);

CREATE INDEX IF NOT EXISTS idx_mcp_server_tool_server ON mcp_server_tool (server_id);
