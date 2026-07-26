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
