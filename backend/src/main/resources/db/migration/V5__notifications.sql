-- V5: notificaciones en la app (Fase 8: avisos de actividad sospechosa).

CREATE TABLE notifications (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind       VARCHAR(32)  NOT NULL CHECK (kind IN ('SUSPICIOUS_LOGIN')),
    message    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    read_at    TIMESTAMPTZ  NULL
);

CREATE INDEX idx_notifications_user_id_created_at ON notifications (user_id, created_at DESC);
