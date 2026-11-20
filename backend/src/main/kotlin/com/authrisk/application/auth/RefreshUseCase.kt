package com.authrisk.application.auth

import com.authrisk.application.port.TransactionRunner
import com.authrisk.domain.port.RefreshTokenGenerator
import com.authrisk.domain.port.RefreshTokenRepository
import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.token.InvalidRefreshTokenException
import java.time.Clock

/**
 * Rota el refresh token: revoca el actual y emite uno nuevo en la misma familia.
 * Si llega un token ya rotado/revocado (reutilización), se revoca toda la familia.
 */
class RefreshUseCase(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val refreshTokenGenerator: RefreshTokenGenerator,
    private val sessionTokens: SessionTokenFactory,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    private sealed interface Outcome {
        data class Rotated(val tokens: AuthTokens) : Outcome
        data object Reused : Outcome
        data object Invalid : Outcome
    }

    fun execute(rawRefreshToken: String?): AuthTokens {
        if (rawRefreshToken.isNullOrBlank()) throw InvalidRefreshTokenException()
        val hash = refreshTokenGenerator.hash(rawRefreshToken)
        val now = clock.instant()

        // El resultado se decide dentro de la transacción y la excepción se lanza fuera,
        // para que la revocación de la familia se confirme y no se revierta.
        val outcome = tx.inTransaction {
            val current = refreshTokens.findByHashForUpdate(hash) ?: return@inTransaction Outcome.Invalid
            when {
                current.isRevoked -> {
                    refreshTokens.revokeFamily(current.familyId, now)
                    Outcome.Reused
                }
                current.isExpired(now) -> Outcome.Invalid
                else -> {
                    val user = users.findById(current.userId) ?: return@inTransaction Outcome.Invalid
                    val issued = sessionTokens.issue(user, current.familyId, now)
                    refreshTokens.save(issued.record)
                    refreshTokens.update(current.rotateTo(issued.record.id, now))
                    Outcome.Rotated(issued.tokens)
                }
            }
        }

        return when (outcome) {
            is Outcome.Rotated -> outcome.tokens
            Outcome.Reused -> throw InvalidRefreshTokenException(reuseDetected = true)
            Outcome.Invalid -> throw InvalidRefreshTokenException()
        }
    }
}
