package com.authrisk.application.twofactor

import com.authrisk.application.auth.AuthTokens
import com.authrisk.application.auth.SessionTokenFactory
import com.authrisk.application.port.TransactionRunner
import com.authrisk.domain.port.ChallengeIdGenerator
import com.authrisk.domain.port.LoginAttemptRepository
import com.authrisk.domain.port.LoginChallengeRepository
import com.authrisk.domain.port.RefreshTokenRepository
import com.authrisk.domain.port.TotpSecretCipher
import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.port.UserTotpRepository
import com.authrisk.domain.risk.LoginAttempt
import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.LoginStage
import com.authrisk.domain.risk.RiskAssessment
import com.authrisk.domain.risk.RiskZone
import com.authrisk.domain.totp.LoginChallenge
import com.authrisk.domain.totp.Totp
import com.authrisk.domain.user.InvalidCredentialsException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Crea el desafío de un login en zona CHALLENGE. Hace el mismo trabajo sea la contraseña correcta
 * o no. Si el almacenamiento falla devuelve igual un id (que no existe en BD): la respuesta al
 * cliente no cambia y ese desafío nunca podrá completarse (fail-safe).
 */
class LoginChallengeIssuer(
    private val challenges: LoginChallengeRepository,
    private val ids: ChallengeIdGenerator,
    private val tx: TransactionRunner,
    private val clock: Clock,
    private val ttl: Duration,
) {
    private val log = System.getLogger(LoginChallengeIssuer::class.java.name)

    fun issue(userId: UUID?, passwordOk: Boolean, email: String, ip: String, risk: RiskAssessment): String {
        val rawId = ids.generate()
        val now = clock.instant()
        val challenge = LoginChallenge(
            id = UUID.randomUUID(),
            idHash = ids.hash(rawId),
            userId = userId,
            passwordOk = passwordOk && userId != null,
            email = email,
            ip = ip,
            assessment = risk,
            attempts = 0,
            expiresAt = now.plus(ttl),
            consumedAt = null,
            createdAt = now,
        )
        try {
            tx.inTransaction { challenges.save(challenge) }
        } catch (ex: RuntimeException) {
            // Solo el tipo: nunca el id, el email ni el mensaje.
            log.log(System.Logger.Level.WARNING, "No se pudo guardar el desafío de login: ${ex.javaClass.name}")
        }
        return rawId
    }
}

/**
 * Segundo paso del login en zona CHALLENGE. Solo emite tokens si se cumple todo: el desafío
 * existe, no venció, no se usó, le quedan intentos, la contraseña del login era correcta, el
 * usuario tiene el 2FA activo y el código TOTP es válido y no reutilizado. Cualquier otro caso
 * produce la misma [InvalidCredentialsException]. Cada intento queda en login_attempts (stage TOTP).
 */
class VerifyLoginChallengeUseCase(
    private val challenges: LoginChallengeRepository,
    private val ids: ChallengeIdGenerator,
    private val totpRepo: UserTotpRepository,
    private val cipher: TotpSecretCipher,
    private val totp: Totp,
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val sessionTokens: SessionTokenFactory,
    private val loginAttempts: LoginAttemptRepository,
    private val tx: TransactionRunner,
    private val clock: Clock,
    private val maxAttempts: Int,
) {
    private sealed interface Decision {
        /** challengeId desconocido: no hay desafío que actualizar. */
        data object Unknown : Decision

        data class Rejected(val challenge: LoginChallenge, val invalid: Boolean) : Decision

        data class Accepted(val challenge: LoginChallenge, val tokens: AuthTokens) : Decision
    }

    fun execute(rawChallengeId: String, code: String, ip: String): AuthTokens {
        val now = clock.instant()
        // La decisión (y la emisión de tokens) ocurre con la fila bloqueada; el registro va después.
        val decision = tx.inTransaction { decide(ids.hash(rawChallengeId), code, now) }
        record(decision, ip, now)
        if (decision is Decision.Accepted) return decision.tokens
        throw InvalidCredentialsException()
    }

    private fun decide(idHash: String, code: String, now: Instant): Decision {
        val challenge = challenges.findByHashForUpdate(idHash) ?: return Decision.Unknown
        if (!challenge.isOpen(now, maxAttempts)) return Decision.Rejected(challenge, invalid = true)

        val attempts = challenge.attempts + 1
        val userId = challenge.userId
        val ok = challenge.passwordOk && userId != null && codeAccepted(userId, code, now)
        // Éxito o último intento: el desafío queda cerrado y no admite más verificaciones.
        val closed = ok || attempts >= maxAttempts
        challenges.update(challenge.copy(attempts = attempts, consumedAt = if (closed) now else null))
        if (!ok) return Decision.Rejected(challenge, invalid = false)

        val user = users.findById(userId!!) ?: return Decision.Rejected(challenge, invalid = false)
        val issued = sessionTokens.issue(user, familyId = UUID.randomUUID(), now = now)
        refreshTokens.save(issued.record)
        return Decision.Accepted(challenge, issued.tokens)
    }

    private fun codeAccepted(userId: UUID, code: String, now: Instant): Boolean {
        val config = totpRepo.findByUserId(userId)?.takeIf { it.enabled } ?: return false
        val step = totp.verify(cipher.decrypt(userId, config.encryptedSecret), code, now) ?: return false
        return totpRepo.markStepUsed(userId, step)
    }

    private fun record(decision: Decision, ip: String, now: Instant) {
        val attempt = when (decision) {
            Decision.Unknown -> attempt("", ip, RiskAssessment(null, RiskZone.CHALLENGE, listOf(RiskAssessment.CHALLENGE_INVALID)), LoginOutcome.FAILURE, now)
            is Decision.Rejected -> attempt(decision.challenge.email, ip, decision.challenge.assessment.marked(decision.invalid), LoginOutcome.FAILURE, now)
            is Decision.Accepted -> attempt(decision.challenge.email, ip, decision.challenge.assessment, LoginOutcome.SUCCESS, now)
        }
        tx.inTransaction { loginAttempts.save(attempt) }
    }

    private fun RiskAssessment.marked(invalid: Boolean) =
        if (invalid) copy(triggeredRules = triggeredRules + RiskAssessment.CHALLENGE_INVALID) else this

    private fun attempt(email: String, ip: String, risk: RiskAssessment, outcome: LoginOutcome, now: Instant) =
        LoginAttempt(UUID.randomUUID(), email, ip, risk, outcome, now, LoginStage.TOTP)
}

/** Borra los desafíos vencidos o cerrados para que la tabla no crezca sin límite. */
class PurgeLoginChallengesUseCase(
    private val challenges: LoginChallengeRepository,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(): Int = tx.inTransaction { challenges.deleteClosed(clock.instant()) }
}
