package com.authrisk.application.notification

import com.authrisk.domain.notification.Notification
import com.authrisk.domain.port.NotificationRepository
import java.time.Clock
import java.util.UUID

/** Últimas notificaciones del usuario autenticado (leídas y no leídas). */
class ListNotificationsUseCase(private val notifications: NotificationRepository) {
    fun execute(userId: UUID): List<Notification> = notifications.findRecentByUserId(userId, LIMIT)

    private companion object {
        const val LIMIT = 20
    }
}

/** Marca una notificación como leída. No distingue "no existe" de "es de otro usuario" (mismo 204). */
class MarkNotificationReadUseCase(private val notifications: NotificationRepository, private val clock: Clock) {
    fun execute(id: UUID, userId: UUID) {
        notifications.markRead(id, userId, clock.instant())
    }
}
