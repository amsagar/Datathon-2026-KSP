CREATE TABLE IF NOT EXISTS chat_session_summary (
    session_id               UUID PRIMARY KEY REFERENCES chat_session (id) ON DELETE CASCADE,
    summary                  TEXT NOT NULL,
    summarized_through_count INT  NOT NULL,
    updated_at               BIGINT NOT NULL
);
