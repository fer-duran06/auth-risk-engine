package com.authrisk.domain.port

import com.authrisk.domain.notification.Notification
import java.time.Instant
import java.util.UUID

/** Persistencia de notificaciones en la app. */
interface NotificationRepository {
    fun save(notification: Notification)

    /** Más recientes primero, como máximo [limit]. */
    fun findRecentByUserId(userId: UUID, limit: Int): List<Notification>

    /** Marca como leída solo si pertenece a [userId]. Devuelve false si no existe o es de otro usuario. */
    fun markRead(id: UUID, userId: UUID, now: Instant): Boolean
}
