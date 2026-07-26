-- Long-term semantic memory: durable, cross-session facts the agent learns about a user (and/or an
-- assistant), recalled by keyword overlap and injected into future turns. Companion to the
-- session-scoped transcript/summary in chat_session_summary.
--
-- Vector embeddings were removed with the Catalyst migration (no pgvector); recall pre-filters by
-- (user_id, assistant_id) and ranks the small candidate set by keyword overlap in the service layer.

CREATE TABLE IF NOT EXISTS semantic_fact (
    id               UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id          VARCHAR(320),                  -- owner UPN; NULL = assistant-shared knowledge
    assistant_id     UUID,                          -- scope; NULL = applies across all the user's assistants
    session_id       UUID,                          -- provenance: the chat that produced the fact
    subject          TEXT NOT NULL,
    predicate        TEXT NOT NULL,
    object           TEXT NOT NULL,
    confidence       REAL NOT NULL DEFAULT 0.7,      -- extractor confidence, 0..1
    importance       REAL NOT NULL DEFAULT 1.0,      -- decays over time, reinforced on recall
    superseded       BOOLEAN NOT NULL DEFAULT FALSE, -- soft-retract when a newer fact conflicts
    created_at       BIGINT NOT NULL,
    last_accessed_at BIGINT NOT NULL
);

-- Recall pre-filters by owner/assistant/active before ranking, so a btree on the scope keys keeps
-- the candidate set small.
CREATE INDEX IF NOT EXISTS idx_semantic_fact_scope
    ON semantic_fact (user_id, assistant_id, superseded);
