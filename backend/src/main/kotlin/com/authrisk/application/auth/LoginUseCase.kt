package com.authrisk.application.auth

import com.authrisk.application.port.TransactionRunner
import com.authrisk.domain.port.PasswordHasher
import com.authrisk.domain.port.RefreshTokenRepository
import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.InvalidCredentialsException
import java.time.Clock
import java.util.UUID

/**
 * Valida credenciales y abre una sesión nueva (nueva familia de refresh tokens).
 * Usuario inexistente y contraseña incorrecta producen la misma excepción; en el primer caso
 * se verifica contra un hash ficticio para igualar el costo de Argon2.
 */
class LoginUseCase(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionTokens: SessionTokenFactory,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    // Hash de un valor aleatorio descartado: nunca coincide con ninguna contraseña real.
    private val dummyHash: String = passwordHasher.hash(UUID.randomUUID().toString())

    fun execute(rawEmail: String, rawPassword: String): AuthTokens {
        val user = Email.parseOrNull(rawEmail)?.let { users.findByEmail(it) }
        if (user == null) {
            passwordHasher.matches(rawPassword, dummyHash)
            throw InvalidCredentialsException()
        }
        if (!passwordHasher.matches(rawPassword, user.passwordHash)) throw InvalidCredentialsException()

        val issued = sessionTokens.issue(user, familyId = UUID.randomUUID(), now = clock.instant())
        tx.inTransaction { refreshTokens.save(issued.record) }
        return issued.tokens
    }
}
