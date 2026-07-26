-- A view-only, frozen snapshot of a conversation, shareable to other authenticated users.
-- One share per session (session_id UNIQUE); re-sharing refreshes the snapshot at the same id.
-- messages_json holds the rendered messages captured at share time, so later turns never leak.
CREATE TABLE IF NOT EXISTS chat_share (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id     UUID NOT NULL UNIQUE REFERENCES chat_session (id) ON DELETE CASCADE,
    created_by     VARCHAR(320) NOT NULL,
    title          VARCHAR(200) NOT NULL,
    assistant_name VARCHAR(200),
    messages_json  TEXT NOT NULL,
    message_count  INT NOT NULL,
    created_at     BIGINT NOT NULL,
    updated_at     BIGINT NOT NULL
);
