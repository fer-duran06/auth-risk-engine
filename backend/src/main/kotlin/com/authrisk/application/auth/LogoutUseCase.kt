package com.authrisk.application.auth

import com.authrisk.application.port.TransactionRunner
import com.authrisk.domain.port.RefreshTokenGenerator
import com.authrisk.domain.port.RefreshTokenRepository
import java.time.Clock

/** Cierra la sesión revocando toda la familia del refresh token. Idempotente: un token desconocido no falla. */
class LogoutUseCase(
    private val refreshTokens: RefreshTokenRepository,
    private val refreshTokenGenerator: RefreshTokenGenerator,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(rawRefreshToken: String?) {
        if (rawRefreshToken.isNullOrBlank()) return
        val hash = refreshTokenGenerator.hash(rawRefreshToken)
        tx.inTransaction {
            refreshTokens.findByHashForUpdate(hash)?.let { refreshTokens.revokeFamily(it.familyId, clock.instant()) }
        }
    }
}
