package com.authrisk.domain.notification

import java.time.Instant
import java.util.UUID

/** Tipo de notificación en la app (no por correo: ver la nota de diseño en el informe). */
enum class NotificationKind {
    /** La cuenta recibió un CHALLENGE (riesgo elevado en un intento de login). */
    SUSPICIOUS_LOGIN,
}

/** Notificación dentro de la app para el usuario dueño de la cuenta. */
data class Notification(
    val id: UUID,
    val userId: UUID,
    val kind: NotificationKind,
    val message: String,
    val createdAt: Instant,
    val readAt: Instant?,
) {
    val unread: Boolean get() = readAt == null
}
