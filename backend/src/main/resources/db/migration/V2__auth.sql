-- V2: usuarios y refresh tokens (Fase 1: autenticación básica).

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    -- Siempre en minúsculas: la unicidad no depende de mayúsculas.
    email         VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    -- Hash Argon2id codificado (incluye parámetros y sal); nunca la contraseña.
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 en hex del token opaco; el valor en claro solo existe en la cookie del cliente.
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    -- Todos los tokens rotados desde un mismo login comparten familia.
    family_id   UUID        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ NULL,
    replaced_by UUID        NULL REFERENCES refresh_tokens (id),
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
