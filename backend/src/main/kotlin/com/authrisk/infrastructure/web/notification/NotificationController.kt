package com.authrisk.infrastructure.web.notification

import com.authrisk.application.notification.ListNotificationsUseCase
import com.authrisk.application.notification.MarkNotificationReadUseCase
import com.authrisk.domain.notification.Notification
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class NotificationResponse(val id: UUID, val kind: String, val message: String, val createdAt: Instant, val read: Boolean) {
    companion object {
        fun from(n: Notification) = NotificationResponse(n.id, n.kind.name, n.message, n.createdAt, !n.unread)
    }
}

@Tag(name = "Notificaciones")
@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val list: ListNotificationsUseCase,
    private val markRead: MarkNotificationReadUseCase,
) {
    @Operation(summary = "Últimas notificaciones del usuario autenticado.", security = [SecurityRequirement(name = "bearerAuth")])
    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt): List<NotificationResponse> =
        list.execute(UUID.fromString(jwt.subject)).map(NotificationResponse::from)

    @Operation(summary = "Marca una notificación como leída.", security = [SecurityRequirement(name = "bearerAuth")])
    @PostMapping("/{id}/read")
    fun read(@PathVariable id: UUID, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<Void> {
        markRead.execute(id, UUID.fromString(jwt.subject))
        return ResponseEntity.noContent().build()
    }
}
