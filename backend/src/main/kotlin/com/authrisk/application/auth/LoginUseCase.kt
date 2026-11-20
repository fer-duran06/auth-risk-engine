package com.authrisk.application.auth

import com.authrisk.application.port.TransactionRunner
import com.authrisk.domain.port.LoginAttemptRepository
import com.authrisk.domain.port.PasswordHasher
import com.authrisk.domain.port.RefreshTokenRepository
import com.authrisk.domain.port.RiskCounters
import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.risk.ChallengeRequiredException
import com.authrisk.domain.risk.LoginAttempt
import com.authrisk.domain.risk.LoginBlockedException
import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.RiskAssessment
import com.authrisk.domain.risk.RiskCountersUnavailableException
import com.authrisk.domain.risk.RiskEngine
import com.authrisk.domain.risk.RiskZone
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.InvalidCredentialsException
import java.time.Clock
import java.util.UUID

/**
 * Login con evaluación de riesgo previa a la verificación de la contraseña:
 * - BLOCK: se rechaza sin buscar al usuario ni verificar la contraseña.
 * - CHALLENGE: se verifica la contraseña, pero si es correcta no se emiten tokens (falta 2FA).
 * - ALLOW: flujo normal; abre una sesión nueva (nueva familia de refresh tokens).
 * Si los contadores no responden se aplica el fail-safe (CHALLENGE).
 *
 * Usuario inexistente y contraseña incorrecta producen la misma excepción; en el primer caso
 * se verifica contra un hash ficticio para igualar el costo de Argon2.
 */
class LoginUseCase(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionTokens: SessionTokenFactory,
    private val riskEngine: RiskEngine,
    private val riskCounters: RiskCounters,
    private val loginAttempts: LoginAttemptRepository,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    // Hash de un valor aleatorio descartado: nunca coincide con ninguna contraseña real.
    private val dummyHash: String = passwordHasher.hash(UUID.randomUUID().toString())

    fun execute(rawEmail: String, rawPassword: String, ip: String): AuthTokens {
        val email = Email.parseOrNull(rawEmail)
        // Identidad para contadores y registro: también los emails malformados cuentan.
        val attemptedEmail = email?.value ?: rawEmail.trim().lowercase().take(Email.MAX_LENGTH)
        val risk = assess(ip, attemptedEmail)

        fun finish(outcome: LoginOutcome) = record(attemptedEmail, ip, risk, outcome)

        if (risk.zone == RiskZone.BLOCK) {
            finish(LoginOutcome.BLOCKED)
            throw LoginBlockedException()
        }

        val user = email?.let { users.findByEmail(it) }
        val passwordOk = if (user == null) {
            passwordHasher.matches(rawPassword, dummyHash)
            false
        } else {
            passwordHasher.matches(rawPassword, user.passwordHash)
        }
        if (user == null || !passwordOk) {
            finish(LoginOutcome.FAILURE)
            throw InvalidCredentialsException()
        }

        if (risk.zone == RiskZone.CHALLENGE) {
            finish(LoginOutcome.CHALLENGED)
            throw ChallengeRequiredException()
        }

        val issued = sessionTokens.issue(user, familyId = UUID.randomUUID(), now = clock.instant())
        tx.inTransaction { refreshTokens.save(issued.record) }
        finish(LoginOutcome.SUCCESS)
        return issued.tokens
    }

    private fun assess(ip: String, email: String): RiskAssessment =
        try {
            riskEngine.assess(riskCounters.read(ip, email))
        } catch (_: RiskCountersUnavailableException) {
            RiskAssessment.failSafe()
        }

    private fun record(email: String, ip: String, risk: RiskAssessment, outcome: LoginOutcome) {
        try {
            riskCounters.record(ip, email, outcome)
        } catch (_: RiskCountersUnavailableException) {
            // El adaptador ya registró el fallo; el intento igual queda guardado en BD.
        }
        val attempt = LoginAttempt(UUID.randomUUID(), email, ip, risk, outcome, clock.instant())
        tx.inTransaction { loginAttempts.save(attempt) }
    }
}
