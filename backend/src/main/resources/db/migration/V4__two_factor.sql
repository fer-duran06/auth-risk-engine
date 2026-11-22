-- V4: 2FA con TOTP y desafíos de login (Fase 4).

CREATE TABLE user_totp (
    user_id          UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    -- base64(nonce ‖ cifrado ‖ tag) con AES-256-GCM; la clave está fuera de la BD (TOTP_ENCRYPTION_KEY).
    secret_encrypted VARCHAR(255) NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    confirmed_at     TIMESTAMPTZ  NULL,
    -- Último paso TOTP aceptado: un código de un paso igual o anterior se rechaza.
    last_used_step   BIGINT       NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    CHECK (enabled = (confirmed_at IS NOT NULL))
);

-- Desafíos emitidos en zona CHALLENGE. Se crean igual sea la contraseña correcta o no;
-- password_ok nunca sale del servidor. Del challengeId solo se guarda su SHA-256.
CREATE TABLE login_challenges (
    id              UUID         PRIMARY KEY,
    id_hash         VARCHAR(64)  NOT NULL UNIQUE,
    user_id         UUID         NULL REFERENCES users (id) ON DELETE CASCADE,
    password_ok     BOOLEAN      NOT NULL,
    email           VARCHAR(254) NOT NULL,
    ip              VARCHAR(45)  NOT NULL,
    -- Evaluación del login que originó el desafío (para registrar los intentos de verificación).
    score           SMALLINT     NULL CHECK (score BETWEEN 0 AND 100),
    triggered_rules TEXT[]       NOT NULL DEFAULT '{}',
    attempts        SMALLINT     NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    expires_at      TIMESTAMPTZ  NOT NULL,
    -- Usado con éxito o invalidado por agotar los intentos.
    consumed_at     TIMESTAMPTZ  NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CHECK (NOT password_ok OR user_id IS NOT NULL)
);

CREATE INDEX idx_login_challenges_expires_at ON login_challenges (expires_at);

-- Distingue el paso de contraseña del paso TOTP en el historial del administrador.
ALTER TABLE login_attempts
    ADD COLUMN stage VARCHAR(8) NOT NULL DEFAULT 'PASSWORD' CHECK (stage IN ('PASSWORD', 'TOTP'));
