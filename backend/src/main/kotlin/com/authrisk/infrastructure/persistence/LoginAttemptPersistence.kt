package com.authrisk.infrastructure.persistence

import com.authrisk.domain.port.LoginAttemptRepository
import com.authrisk.domain.risk.LoginAttempt
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "login_attempts")
class LoginAttemptJpaEntity(
    @Id
    var id: UUID,
    @Column(nullable = false, length = 254)
    var email: String,
    @Column(nullable = false, length = 45)
    var ip: String,
    @Column(columnDefinition = "smallint")
    var score: Short?,
    @Column(nullable = false, length = 10)
    var zone: String,
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "triggered_rules", nullable = false, columnDefinition = "text[]")
    var triggeredRules: Array<String>,
    @Column(nullable = false)
    var success: Boolean,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    companion object {
        fun from(a: LoginAttempt) = LoginAttemptJpaEntity(
            id = a.id,
            email = a.email,
            ip = a.ip,
            score = a.assessment.score?.toShort(),
            zone = a.assessment.zone.name,
            triggeredRules = a.assessment.triggeredRules.toTypedArray(),
            success = a.success,
            createdAt = a.createdAt,
        )
    }
}

/** Consultas de lectura (pruebas y, más adelante, el dashboard de administración). */
interface LoginAttemptJpaRepository : JpaRepository<LoginAttemptJpaEntity, UUID> {
    fun findByIpOrderByCreatedAtAsc(ip: String): List<LoginAttemptJpaEntity>
}

@Component
class JpaLoginAttemptRepositoryAdapter(private val entityManager: EntityManager) : LoginAttemptRepository {
    // persist directo: el id lo asigna el dominio. Requiere una transacción activa.
    override fun save(attempt: LoginAttempt) = entityManager.persist(LoginAttemptJpaEntity.from(attempt))
}
