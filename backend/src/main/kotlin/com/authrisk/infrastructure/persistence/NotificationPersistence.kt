package com.authrisk.infrastructure.persistence

import com.authrisk.domain.notification.Notification
import com.authrisk.domain.notification.NotificationKind
import com.authrisk.domain.port.NotificationRepository
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "notifications")
class NotificationJpaEntity(
    @Id
    var id: UUID,
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(nullable = false, length = 32)
    var kind: String,
    @Column(nullable = false, columnDefinition = "text")
    var message: String,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
    @Column(name = "read_at")
    var readAt: Instant?,
) {
    fun toDomain() = Notification(id, userId, NotificationKind.valueOf(kind), message, createdAt, readAt)

    companion object {
        fun from(n: Notification) = NotificationJpaEntity(n.id, n.userId, n.kind.name, n.message, n.createdAt, n.readAt)
    }
}

interface NotificationJpaRepository : JpaRepository<NotificationJpaEntity, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(userId: UUID, limit: Limit): List<NotificationJpaEntity>

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update NotificationJpaEntity n set n.readAt = :now where n.id = :id and n.userId = :userId and n.readAt is null")
    fun markRead(@Param("id") id: UUID, @Param("userId") userId: UUID, @Param("now") now: Instant): Int
}

/** Requiere una transacción activa para `save` (la abre el caso de uso con TransactionRunner). */
@Component
class JpaNotificationRepositoryAdapter(
    private val jpa: NotificationJpaRepository,
    private val entityManager: EntityManager,
) : NotificationRepository {

    override fun save(notification: Notification) = entityManager.persist(NotificationJpaEntity.from(notification))

    override fun findRecentByUserId(userId: UUID, limit: Int): List<Notification> =
        jpa.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(limit)).map { it.toDomain() }

    override fun markRead(id: UUID, userId: UUID, now: Instant): Boolean = jpa.markRead(id, userId, now) > 0
}
