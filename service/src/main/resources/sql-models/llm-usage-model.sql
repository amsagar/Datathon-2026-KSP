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

ALTER TABLE llm_usage_event ADD COLUMN IF NOT EXISTS usage_kind VARCHAR(16) NOT NULL DEFAULT 'chat';
ALTER TABLE llm_usage_event ADD COLUMN IF NOT EXISTS usage_source VARCHAR(32) NOT NULL DEFAULT 'main';

CREATE INDEX IF NOT EXISTS idx_llm_usage_user_time
    ON llm_usage_event (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_usage_model_time
    ON llm_usage_event (model_name, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_llm_usage_created_at
    ON llm_usage_event (created_at DESC);
