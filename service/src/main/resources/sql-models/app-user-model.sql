CREATE TABLE IF NOT EXISTS app_user (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(200),
    email         VARCHAR(320),
    -- Comma-separated application roles: ADMIN, SUPERVISOR, INVESTIGATOR, ANALYST, POLICYMAKER
    roles         TEXT NOT NULL DEFAULT 'ANALYST',
    created_at    BIGINT NOT NULL
);

-- Seed one user per role for the demo. Shared password: Password@123
INSERT INTO app_user (username, password_hash, display_name, email, roles, created_at)
VALUES
    ('admin',        '$2y$10$sdHWAqLSAxvgPQNcouAxKeQNQco/2HZfNJnMtP5bDGccbonaFkUAK', 'System Administrator',  'admin@ksp.gov.in',        'ADMIN',        extract(epoch from now())::bigint * 1000),
    ('supervisor',   '$2y$10$sdHWAqLSAxvgPQNcouAxKeQNQco/2HZfNJnMtP5bDGccbonaFkUAK', 'Shift Supervisor',      'supervisor@ksp.gov.in',   'SUPERVISOR',   extract(epoch from now())::bigint * 1000),
    ('investigator', '$2y$10$sdHWAqLSAxvgPQNcouAxKeQNQco/2HZfNJnMtP5bDGccbonaFkUAK', 'Investigating Officer', 'investigator@ksp.gov.in', 'INVESTIGATOR', extract(epoch from now())::bigint * 1000),
    ('analyst',      '$2y$10$sdHWAqLSAxvgPQNcouAxKeQNQco/2HZfNJnMtP5bDGccbonaFkUAK', 'Crime Analyst',         'analyst@ksp.gov.in',      'ANALYST',      extract(epoch from now())::bigint * 1000),
    ('policymaker',  '$2y$10$sdHWAqLSAxvgPQNcouAxKeQNQco/2HZfNJnMtP5bDGccbonaFkUAK', 'Policy Maker',          'policymaker@ksp.gov.in',  'POLICYMAKER',  extract(epoch from now())::bigint * 1000)
ON CONFLICT (username) DO NOTHING;

-- User Management extensions (idempotent). Seed users keep their defaults: enabled=TRUE,
-- must_change_password=FALSE.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS date_of_birth DATE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS phone VARCHAR(32);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS designation VARCHAR(120);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS department VARCHAR(160);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS photo BYTEA;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS photo_content_type VARCHAR(100);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS last_login_at BIGINT;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS updated_at BIGINT;
