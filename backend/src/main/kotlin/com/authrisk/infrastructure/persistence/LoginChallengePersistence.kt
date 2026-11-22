package com.authrisk.infrastructure.persistence

import com.authrisk.domain.port.LoginChallengeRepository
import com.authrisk.domain.risk.RiskAssessment
import com.authrisk.domain.risk.RiskZone
import com.authrisk.domain.totp.LoginChallenge
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "login_challenges")
class LoginChallengeJpaEntity(
    @Id
    var id: UUID,
    @Column(name = "id_hash", nullable = false, unique = true, length = 64)
    var idHash: String,
    @Column(name = "user_id")
    var userId: UUID?,
    @Column(name = "password_ok", nullable = false)
    var passwordOk: Boolean,
    @Column(nullable = false, length = 254)
    var email: String,
    @Column(nullable = false, length = 45)
    var ip: String,
    @Column(columnDefinition = "smallint")
    var score: Short?,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "triggered_rules", nullable = false, columnDefinition = "text[]")
    var triggeredRules: Array<String>,
    @Column(nullable = false, columnDefinition = "smallint")
    var attempts: Short,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "consumed_at")
    var consumedAt: Instant?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    // Un desafío solo existe en zona CHALLENGE.
    fun toDomain() = LoginChallenge(
        id, idHash, userId, passwordOk, email, ip,
        RiskAssessment(score?.toInt(), RiskZone.CHALLENGE, triggeredRules.toList()),
        attempts.toInt(), expiresAt, consumedAt, createdAt,
    )

    companion object {
        fun from(c: LoginChallenge) = LoginChallengeJpaEntity(
            c.id, c.idHash, c.userId, c.passwordOk, c.email, c.ip,
            c.assessment.score?.toShort(), c.assessment.triggeredRules.toTypedArray(),
            c.attempts.toShort(), c.expiresAt, c.consumedAt, c.createdAt,
        )
    }
}

interface LoginChallengeJpaRepository : JpaRepository<LoginChallengeJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByIdHash(idHash: String): LoginChallengeJpaEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LoginChallengeJpaEntity c where c.expiresAt <= :now or c.consumedAt is not null")
    fun deleteClosed(@Param("now") now: Instant): Int
}

/** Requiere una transacción activa (la abre el caso de uso con TransactionRunner). */
@Component
class JpaLoginChallengeRepositoryAdapter(
    private val jpa: LoginChallengeJpaRepository,
    private val entityManager: EntityManager,
) : LoginChallengeRepository {

    override fun save(challenge: LoginChallenge) = entityManager.persist(LoginChallengeJpaEntity.from(challenge))

    override fun findByHashForUpdate(idHash: String): LoginChallenge? = jpa.findByIdHash(idHash)?.toDomain()

    override fun update(challenge: LoginChallenge) {
        val entity = entityManager.find(LoginChallengeJpaEntity::class.java, challenge.id)
            ?: error("Desafío inexistente: ${challenge.id}")
        entity.attempts = challenge.attempts.toShort()
        entity.consumedAt = challenge.consumedAt
    }

    override fun deleteClosed(now: Instant): Int = jpa.deleteClosed(now)
}
