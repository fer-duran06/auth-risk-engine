-- V3: intentos de login con su evaluación de riesgo (Fase 2: motor de riesgo).
-- Solo para administración: el score y las reglas nunca se devuelven al usuario.

CREATE TABLE login_attempts (
    id              UUID         PRIMARY KEY,
    -- Email tal como se intentó, normalizado; puede no corresponder a ninguna cuenta.
    email           VARCHAR(254) NOT NULL,
    ip              VARCHAR(45)  NOT NULL,
    -- NULL solo en el fail-safe (contadores no disponibles): no se inventa un número.
    score           SMALLINT     NULL CHECK (score BETWEEN 0 AND 100),
    zone            VARCHAR(10)  NOT NULL CHECK (zone IN ('ALLOW', 'CHALLENGE', 'BLOCK')),
    triggered_rules TEXT[]       NOT NULL DEFAULT '{}',
    -- true solo si se emitieron tokens.
    success         BOOLEAN      NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_login_attempts_created_at ON login_attempts (created_at);
CREATE INDEX idx_login_attempts_ip ON login_attempts (ip);
