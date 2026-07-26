CREATE TABLE IF NOT EXISTS chat_session (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(200) NOT NULL,
    archived    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  BIGINT NOT NULL,
    updated_at  BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_session_archived_updated
    ON chat_session (archived, updated_at DESC);

-- Per-user scoping: each session is owned by the authenticated user's UPN
-- (JWT subject). Pre-existing owner-less rows are backfilled to the local
-- bypass user; change the literal below to claim them for a different UPN.
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS user_id VARCHAR(320);
UPDATE chat_session SET user_id = 'system@ksp.com' WHERE user_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_chat_session_user_archived_updated
    ON chat_session (user_id, archived, updated_at DESC);

-- Temporary chats: persisted and viewable like normal chats, but auto-deleted after a retention
-- window (default 30 days, by a scheduled purge keyed on updated_at) and isolated from long-term
-- semantic memory (no recall, no consolidation, no memory tools).
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS temporary BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_chat_session_temporary_updated
    ON chat_session (temporary, updated_at) WHERE temporary = TRUE;

-- Dormant column retained after the LLM-provider registry was removed (single in-house model now).
-- Kept nullable and FK-free so existing session read/write mappings continue to work unchanged.
ALTER TABLE chat_session ADD COLUMN IF NOT EXISTS provider_id UUID;
