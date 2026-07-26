-- =============================================================================
-- FULL SCHEMA - Sequential Migration
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. EXTENSIONS
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS vector;


-- -----------------------------------------------------------------------------
-- 1. ASSISTANT  (root; everything else references this)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assistant (
                                         id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(200) NOT NULL,
    system_prompt TEXT NOT NULL,
    builtin_tools TEXT NOT NULL DEFAULT '',
    -- NULL = platform-skill defaults apply; non-null = explicit comma-separated id list.
    platform_skills TEXT,
    created_at    BIGINT NOT NULL,
    updated_at    BIGINT NOT NULL
    );


-- -----------------------------------------------------------------------------
-- 2. CHAT SESSION
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_session (
                                            id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(200) NOT NULL,
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    assistant_id UUID REFERENCES assistant (id),
    user_id     VARCHAR(320),
    style_id    UUID,           -- FK added after response_style is created (see step 7)
    created_at  BIGINT NOT NULL,
    updated_at  BIGINT NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_chat_session_archived_updated
    ON chat_session (archived, updated_at DESC);

UPDATE chat_session SET user_id = 'system@ksp.com' WHERE user_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_session_user_archived_updated
    ON chat_session (user_id, archived, updated_at DESC);


-- -----------------------------------------------------------------------------
-- 3. CHAT SESSION SUMMARY
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_session_summary (
                                                    session_id               UUID PRIMARY KEY REFERENCES chat_session (id) ON DELETE CASCADE,
    summary                  TEXT NOT NULL,
    summarized_through_count INT  NOT NULL,
    updated_at               BIGINT NOT NULL
    );


-- -----------------------------------------------------------------------------
-- 4. CHAT TOOL EVENT
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_tool_event (
                                               id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL,
    turn_index  INT  NOT NULL,
    seq         INT  NOT NULL,
    call_id     VARCHAR(64) NOT NULL,
    tool_name   VARCHAR(200) NOT NULL,
    tool_input  TEXT,
    tool_output TEXT,
    is_error    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  BIGINT NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_chat_tool_event_session_turn
    ON chat_tool_event (session_id, turn_index, seq);


-- -----------------------------------------------------------------------------
-- 5. LLM USAGE EVENT
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS llm_usage_event (
                                               id                BIGSERIAL PRIMARY KEY,
                                               request_id        VARCHAR(64) NOT NULL,
    session_id        UUID NOT NULL,
    user_id           VARCHAR(320) NOT NULL,
    assistant_id      UUID,
    usage_kind        VARCHAR(16) NOT NULL DEFAULT 'chat',
    usage_source      VARCHAR(32) NOT NULL DEFAULT 'main',
    model_name        VARCHAR(128) NOT NULL,
    prompt_tokens     INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens      INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_llm_usage_user_time
    ON llm_usage_event (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_usage_model_time
    ON llm_usage_event (model_name, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_usage_created_at
    ON llm_usage_event (created_at DESC);


-- -----------------------------------------------------------------------------
-- 6. TOOL AUTH PROFILE
-- -----------------------------------------------------------------------------
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

CREATE INDEX IF NOT EXISTS idx_tool_auth_assistant ON tool_auth_profile (assistant_id);


-- -----------------------------------------------------------------------------
-- 7. RESPONSE STYLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS response_style (
                                              id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    instructions TEXT NOT NULL,
    is_default   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   BIGINT NOT NULL,
    updated_at   BIGINT NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_response_style_assistant ON response_style (assistant_id);

CREATE INDEX IF NOT EXISTS idx_response_style_assistant_default
    ON response_style (assistant_id) WHERE is_default = TRUE;

-- Now that response_style exists, add the FK from chat_session
ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS style_id UUID REFERENCES response_style (id) ON DELETE SET NULL;


-- -----------------------------------------------------------------------------
-- 8. AGENT TOOL  (depends on assistant + tool_auth_profile)
-- -----------------------------------------------------------------------------
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
    embedding       vector(3072),
    embedding_hash  VARCHAR(64),
    group_id        UUID,           -- FK added after agent_tool_group is created (see step 9)
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_agent_tool_assistant ON agent_tool (assistant_id);


-- -----------------------------------------------------------------------------
-- 9. AGENT TOOL GROUP  (depends on assistant + agent_tool)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_tool_group (
                                                id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    source_type  VARCHAR(30) NOT NULL DEFAULT 'manual',
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   BIGINT NOT NULL,
    updated_at   BIGINT NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_agent_tool_group_assistant ON agent_tool_group (assistant_id);

-- Now that agent_tool_group exists, add the FK from agent_tool
ALTER TABLE agent_tool
    ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES agent_tool_group (id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_agent_tool_group ON agent_tool (group_id);


-- -----------------------------------------------------------------------------
-- 10. AGENT SKILL
-- -----------------------------------------------------------------------------
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


-- -----------------------------------------------------------------------------
-- 11. AGENT DOCUMENT
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_document (
                                              id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name         VARCHAR(300) NOT NULL,
    blob_prefix  TEXT NOT NULL,
    chunk_count  INT NOT NULL DEFAULT 0,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   BIGINT NOT NULL,
    updated_at   BIGINT NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_agent_document_assistant ON agent_document (assistant_id);


-- -----------------------------------------------------------------------------
-- 12. MCP SERVER + MCP SERVER TOOL
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mcp_server (
                                          id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id            UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name                    VARCHAR(200) NOT NULL,
    description             TEXT,
    transport               VARCHAR(40)  NOT NULL DEFAULT 'streamable_http',
    url                     TEXT NOT NULL,
    sse_endpoint            TEXT,
    auth_type               VARCHAR(40)  NOT NULL DEFAULT 'none',
    auth_config             TEXT,
    encrypted_secret        TEXT,
    encrypted_access_token  TEXT,
    token_expires_at        BIGINT,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    status                  VARCHAR(40),
    status_detail           TEXT,
    created_at              BIGINT NOT NULL,
    updated_at              BIGINT NOT NULL
    );

CREATE INDEX IF NOT EXISTS idx_mcp_server_assistant ON mcp_server (assistant_id);

CREATE TABLE IF NOT EXISTS mcp_server_tool (
                                               id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id    UUID NOT NULL REFERENCES mcp_server (id) ON DELETE CASCADE,
    name         VARCHAR(300) NOT NULL,
    description  TEXT,
    input_schema TEXT,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   BIGINT NOT NULL,
    updated_at   BIGINT NOT NULL,
    UNIQUE (server_id, name)
    );

CREATE INDEX IF NOT EXISTS idx_mcp_server_tool_server ON mcp_server_tool (server_id);


-- -----------------------------------------------------------------------------
-- 13. VECTOR STORE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vector_store (
                                            id        UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    content   TEXT,
    metadata  JSON,
    embedding vector(3072)
    );


-- =============================================================================
-- END OF SCHEMA
-- =============================================================================