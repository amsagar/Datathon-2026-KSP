-- Rotating "starter prompt" suggestions shown on the empty chat screen.
-- Rows with user_id IS NULL are assistant-level (seeded, or LLM-generated from the assistant's own
-- name + system prompt). Rows with a user_id are personalized for that user (generated from their
-- memories + recent sessions). `source` = seed | assistant | user; `enabled` gates display.
CREATE TABLE IF NOT EXISTS prompt_suggestion (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assistant_id UUID NOT NULL REFERENCES assistant (id) ON DELETE CASCADE,
    user_id      VARCHAR(255),
    text         TEXT NOT NULL,
    lang         VARCHAR(8) NOT NULL DEFAULT 'en',
    source       VARCHAR(16) NOT NULL DEFAULT 'seed',
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   BIGINT NOT NULL,
    updated_at   BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_prompt_suggestion_assistant_lang
    ON prompt_suggestion (assistant_id, lang);
CREATE INDEX IF NOT EXISTS idx_prompt_suggestion_user
    ON prompt_suggestion (assistant_id, user_id, lang);

-- Curated seeds for the "Crime Intelligence" assistant (fixed UUID from crime-assistant-seed.sql).
-- Idempotent: INSERT ... SELECT ... WHERE NOT EXISTS so schema-init on every restart never duplicates
-- (the Spring script runner splits on ';' and can't use dollar-quoted DO blocks).
INSERT INTO prompt_suggestion (assistant_id, user_id, text, lang, source, enabled, created_at, updated_at)
SELECT '11111111-1111-1111-1111-111111111111', NULL, v.text, 'en', 'seed', TRUE,
       extract(epoch from now())::bigint * 1000, extract(epoch from now())::bigint * 1000
FROM (VALUES
    ('Top 5 districts by number of heinous crimes this year'),
    ('Monthly trend of theft cases in Bengaluru'),
    ('Who are the highest-risk repeat offenders?'),
    ('Which crime types are rising over the last 90 days?'),
    ('Show the co-offender network for the busiest police station'),
    ('Districts with the most cases still under investigation')
) AS v(text)
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_suggestion p
    WHERE p.assistant_id = '11111111-1111-1111-1111-111111111111'
      AND p.lang = 'en' AND p.source = 'seed' AND p.user_id IS NULL AND p.text = v.text
);

INSERT INTO prompt_suggestion (assistant_id, user_id, text, lang, source, enabled, created_at, updated_at)
SELECT '11111111-1111-1111-1111-111111111111', NULL, v.text, 'kn', 'seed', TRUE,
       extract(epoch from now())::bigint * 1000, extract(epoch from now())::bigint * 1000
FROM (VALUES
    ('ಈ ವರ್ಷ ಘೋರ ಅಪರಾಧಗಳ ಸಂಖ್ಯೆಯಲ್ಲಿ ಅಗ್ರ 5 ಜಿಲ್ಲೆಗಳು'),
    ('ಬೆಂಗಳೂರಿನಲ್ಲಿ ಕಳ್ಳತನ ಪ್ರಕರಣಗಳ ಮಾಸಿಕ ಪ್ರವೃತ್ತಿ ತೋರಿಸಿ'),
    ('ಅತಿ ಹೆಚ್ಚು ಅಪಾಯದ ಪುನರಾವರ್ತಿತ ಅಪರಾಧಿಗಳು ಯಾರು?'),
    ('ಕಳೆದ 90 ದಿನಗಳಲ್ಲಿ ಯಾವ ಅಪರಾಧ ಪ್ರಕಾರಗಳು ಹೆಚ್ಚುತ್ತಿವೆ?'),
    ('ಅತಿ ಹೆಚ್ಚು ಸಕ್ರಿಯ ಠಾಣೆಯ ಸಹ-ಆರೋಪಿ ಜಾಲ ತೋರಿಸಿ')
) AS v(text)
WHERE NOT EXISTS (
    SELECT 1 FROM prompt_suggestion p
    WHERE p.assistant_id = '11111111-1111-1111-1111-111111111111'
      AND p.lang = 'kn' AND p.source = 'seed' AND p.user_id IS NULL AND p.text = v.text
);
