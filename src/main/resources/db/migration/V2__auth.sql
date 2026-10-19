CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER', 'STAFF', 'CUSTOMER')),
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

CREATE TABLE refresh_tokens (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked_at TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
