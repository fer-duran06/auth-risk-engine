package com.authrisk.domain.token

import java.time.Instant
import java.util.UUID

/**
 * Registro de un refresh token opaco. Solo se guarda [tokenHash], nunca el valor en claro.
 * Todos los tokens rotados desde un mismo login comparten [familyId].
 */
data class RefreshToken(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val familyId: UUID,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val replacedBy: UUID?,
    val createdAt: Instant,
) {
    val isRevoked: Boolean get() = revokedAt != null

    fun isExpired(now: Instant): Boolean = !now.isBefore(expiresAt)

    /** Utilizable solo si no fue revocado/rotado y no expiró. */
    fun isActive(now: Instant): Boolean = !isRevoked && !isExpired(now)

    /** Marca este token como rotado hacia [newTokenId]. */
    fun rotateTo(newTokenId: UUID, now: Instant): RefreshToken = copy(revokedAt = now, replacedBy = newTokenId)

    override fun toString(): String =
        "RefreshToken(id=$id, userId=$userId, familyId=$familyId, expiresAt=$expiresAt, revokedAt=$revokedAt, replacedBy=$replacedBy)"
}
