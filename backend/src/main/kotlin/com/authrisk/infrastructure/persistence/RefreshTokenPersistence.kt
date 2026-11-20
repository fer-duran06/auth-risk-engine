package com.authrisk.infrastructure.persistence

import com.authrisk.domain.port.RefreshTokenRepository
import com.authrisk.domain.token.RefreshToken
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.Id
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshTokenJpaEntity(
    @Id
    var id: UUID,
    @Column(name = "user_id", nullable = false)
    var userId: UUID,
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String,
    @Column(name = "family_id", nullable = false)
    var familyId: UUID,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(name = "revoked_at")
    var revokedAt: Instant?,
    @Column(name = "replaced_by")
    var replacedBy: UUID?,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    fun toDomain() = RefreshToken(id, userId, tokenHash, familyId, expiresAt, revokedAt, replacedBy, createdAt)

    companion object {
        fun from(t: RefreshToken) =
            RefreshTokenJpaEntity(t.id, t.userId, t.tokenHash, t.familyId, t.expiresAt, t.revokedAt, t.replacedBy, t.createdAt)
    }
}

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findByTokenHash(tokenHash: String): RefreshTokenJpaEntity?

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    fun revokeFamily(@Param("familyId") familyId: UUID, @Param("now") now: Instant): Int
}

@Component
class JpaRefreshTokenRepositoryAdapter(
    private val jpa: RefreshTokenJpaRepository,
    private val entityManager: EntityManager,
) : RefreshTokenRepository {

    // persist directo: el id lo asigna el dominio, así se evita el SELECT previo de merge().
    override fun save(token: RefreshToken) = entityManager.persist(RefreshTokenJpaEntity.from(token))

    override fun findByHashForUpdate(tokenHash: String): RefreshToken? = jpa.findByTokenHash(tokenHash)?.toDomain()

    override fun update(token: RefreshToken) {
        val entity = entityManager.find(RefreshTokenJpaEntity::class.java, token.id)
            ?: error("Refresh token inexistente: ${token.id}")
        entity.revokedAt = token.revokedAt
        entity.replacedBy = token.replacedBy
    }

    override fun revokeFamily(familyId: UUID, now: Instant) {
        jpa.revokeFamily(familyId, now)
    }
}
