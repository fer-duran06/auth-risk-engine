package com.authrisk.application.auth

import com.authrisk.domain.port.RefreshTokenGenerator
import com.authrisk.domain.port.TokenIssuer
import com.authrisk.domain.token.RefreshToken
import com.authrisk.domain.user.User
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Par emitido: lo que recibe el cliente y el registro (solo hash) que se persiste. */
data class IssuedSession(val tokens: AuthTokens, val record: RefreshToken)

/** Emite un access token y un refresh token nuevo dentro de una familia. Compartido por login y refresh. */
class SessionTokenFactory(
    private val tokenIssuer: TokenIssuer,
    private val refreshTokenGenerator: RefreshTokenGenerator,
    private val refreshTokenTtl: Duration,
) {
    fun issue(user: User, familyId: UUID, now: Instant): IssuedSession {
        val rawRefresh = refreshTokenGenerator.generate()
        val record = RefreshToken(
            id = UUID.randomUUID(),
            userId = user.id,
            tokenHash = refreshTokenGenerator.hash(rawRefresh),
            familyId = familyId,
            expiresAt = now.plus(refreshTokenTtl),
            revokedAt = null,
            replacedBy = null,
            createdAt = now,
        )
        val tokens = AuthTokens(tokenIssuer.issueAccessToken(user), rawRefresh, record.expiresAt)
        return IssuedSession(tokens, record)
    }
}
