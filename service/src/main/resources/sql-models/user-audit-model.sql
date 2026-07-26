CREATE TABLE IF NOT EXISTS user_audit_log (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor      VARCHAR(150),
    action     VARCHAR(80) NOT NULL,
    target     VARCHAR(150),
    details    TEXT,
    created_at BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_audit_created ON user_audit_log(created_at DESC);
