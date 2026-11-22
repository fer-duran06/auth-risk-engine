package com.authrisk.infrastructure.persistence

import com.authrisk.domain.port.UserTotpRepository
import com.authrisk.domain.totp.UserTotp
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_totp")
class UserTotpJpaEntity(
    @Id
    @Column(name = "user_id")
    var userId: UUID,
    @Column(name = "secret_encrypted", nullable = false)
    var secretEncrypted: String,
    @Column(nullable = false)
    var enabled: Boolean,
    @Column(name = "confirmed_at")
    var confirmedAt: Instant?,
    @Column(name = "last_used_step")
    var lastUsedStep: Long?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    fun toDomain() = UserTotp(userId, secretEncrypted, enabled, confirmedAt, lastUsedStep, createdAt)
}

/** Escrituras con SQL condicional: la condición y el cambio ocurren en una sola sentencia atómica. */
interface UserTotpJpaRepository : JpaRepository<UserTotpJpaEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO user_totp (user_id, secret_encrypted, enabled, created_at)
            VALUES (:userId, :secret, FALSE, :now)
            ON CONFLICT (user_id) DO UPDATE
               SET secret_encrypted = EXCLUDED.secret_encrypted, last_used_step = NULL, created_at = EXCLUDED.created_at
             WHERE user_totp.enabled = FALSE
        """,
    )
    fun upsertPending(@Param("userId") userId: UUID, @Param("secret") secret: String, @Param("now") now: Instant): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        nativeQuery = true,
        value = """
            UPDATE user_totp SET last_used_step = :step
             WHERE user_id = :userId AND (last_used_step IS NULL OR last_used_step < :step)
        """,
    )
    fun markStepUsed(@Param("userId") userId: UUID, @Param("step") step: Long): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        nativeQuery = true,
        value = "UPDATE user_totp SET enabled = TRUE, confirmed_at = :now WHERE user_id = :userId AND enabled = FALSE",
    )
    fun enable(@Param("userId") userId: UUID, @Param("now") now: Instant): Int
}

/** Requiere una transacción activa (la abre el caso de uso con TransactionRunner). */
@Component
class JpaUserTotpRepositoryAdapter(private val jpa: UserTotpJpaRepository) : UserTotpRepository {
    override fun findByUserId(userId: UUID): UserTotp? = jpa.findById(userId).orElse(null)?.toDomain()
    override fun savePending(userId: UUID, encryptedSecret: String, now: Instant) = jpa.upsertPending(userId, encryptedSecret, now) == 1
    override fun markStepUsed(userId: UUID, step: Long) = jpa.markStepUsed(userId, step) == 1
    override fun enable(userId: UUID, now: Instant) = jpa.enable(userId, now) == 1
}
