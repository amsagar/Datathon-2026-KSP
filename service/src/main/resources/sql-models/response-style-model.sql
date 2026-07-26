CREATE TABLE IF NOT EXISTS response_style (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    name         VARCHAR(200) NOT NULL,
    description  TEXT,
    instructions TEXT NOT NULL,
    created_at   BIGINT NOT NULL,
    updated_at   BIGINT NOT NULL
);

-- Response styles are now owned by exactly one assistant. Previously they were global presets seeded
-- once. Migrate idempotently without dollar-quoted blocks (Spring's script runner splits on ';' and
-- can't parse PL/pgSQL DO blocks): add the owner column NULLABLE, drop any owner-less rows (only
-- legacy global rows ever have NULL; a session's style_id FK is ON DELETE SET NULL, so pinned
-- sessions stay usable), then enforce NOT NULL. No-op once migrated, so schema init (mode: always)
-- does NOT wipe styles on restart. On a fresh DB the column already exists NOT NULL from CREATE above.
ALTER TABLE response_style ADD COLUMN IF NOT EXISTS assistant_id UUID REFERENCES assistant (id) ON DELETE CASCADE;
DELETE FROM response_style WHERE assistant_id IS NULL;
ALTER TABLE response_style ALTER COLUMN assistant_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_response_style_assistant ON response_style (assistant_id);

-- At most one default style per assistant (enforced in application code).
ALTER TABLE response_style ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_response_style_assistant_default
    ON response_style (assistant_id) WHERE is_default = TRUE;

-- A session may pin a response style; clearing the style on delete keeps the session usable.
ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS style_id UUID REFERENCES response_style (id) ON DELETE SET NULL;
