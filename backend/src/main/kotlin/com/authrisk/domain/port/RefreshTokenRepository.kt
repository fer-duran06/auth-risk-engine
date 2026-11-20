package com.authrisk.domain.port

import com.authrisk.domain.token.RefreshToken
import java.time.Instant
import java.util.UUID

/** Persistencia de refresh tokens (solo hashes). */
interface RefreshTokenRepository {
    fun save(token: RefreshToken)

    /** Busca por hash bloqueando la fila hasta el fin de la transacción (evita doble uso concurrente). */
    fun findByHashForUpdate(tokenHash: String): RefreshToken?

    /** Persiste cambios de revocación/rotación de un token existente. */
    fun update(token: RefreshToken)

    /** Revoca todos los tokens aún activos de la familia. */
    fun revokeFamily(familyId: UUID, now: Instant)
}
