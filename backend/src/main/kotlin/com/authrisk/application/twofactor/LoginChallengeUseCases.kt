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
 *
 * Camino constante: sea cual sea el motivo del fallo, se hace el mismo trabajo (lectura del
 * desafío, lectura de la configuración TOTP, descifrado AES-GCM, 3 HMAC, un UPDATE de user_totp y
 * un UPDATE de login_challenges). Cuando no aplica el dato real se usa un usuario y un secreto
 * ficticios. Así el tiempo de respuesta no revela si la contraseña era correcta ni si hay 2FA.
 * Solo el éxito hace trabajo extra (emitir tokens), y el éxito ya es visible en la respuesta.
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
    private val log = System.getLogger(VerifyLoginChallengeUseCase::class.java.name)

    // Secreto ficticio cifrado con la misma clave: el descifrado cuesta lo mismo que el real.
    private val dummySecret: ByteArray = Totp.newSecret()
    private val dummyEncryptedSecret: String = cipher.encrypt(DUMMY_USER_ID, dummySecret)

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
        val challenge = challenges.findByHashForUpdate(idHash)
        val open = challenge != null && challenge.isOpen(now, maxAttempts)
        // Usuario cuyo código se verifica de verdad; null = camino ficticio.
        val candidate = challenge?.userId?.takeIf { open && challenge.passwordOk }

        val ok = codeAccepted(candidate, code, now)

        // Siempre un UPDATE: con un id desconocido afecta 0 filas; con un desafío cerrado deja sus valores.
        val attempts = when {
            challenge == null -> 0
            open -> challenge.attempts + 1
            else -> challenge.attempts
        }
        val consumedAt = when {
            challenge == null -> null
            !open -> challenge.consumedAt
            // Éxito o último intento: el desafío queda cerrado y no admite más verificaciones.
            ok || attempts >= maxAttempts -> now
            else -> null
        }
        challenges.updateAttempts(idHash, attempts, consumedAt)

        if (challenge == null) return Decision.Unknown
        if (!open) return Decision.Rejected(challenge, invalid = true)
        if (!ok) return Decision.Rejected(challenge, invalid = false)

        val user = users.findById(candidate!!) ?: return Decision.Rejected(challenge, invalid = false)
        val issued = sessionTokens.issue(user, familyId = UUID.randomUUID(), now = now)
        refreshTokens.save(issued.record)
        return Decision.Accepted(challenge, issued.tokens)
    }

    /**
     * Misma secuencia para todo [candidate], real o null: leer la configuración, descifrar,
     * verificar y un UPDATE de last_used_step. Si el descifrado falla (clave cambiada o dato
     * corrupto), el código no se acepta: mismo 401, nunca un 500.
     */
    private fun codeAccepted(candidate: UUID?, code: String, now: Instant): Boolean {
        val config = totpRepo.findByUserId(candidate ?: DUMMY_USER_ID)
        val real = candidate != null && config != null && config.enabled
        val owner = if (real) candidate!! else DUMMY_USER_ID
        val secret = try {
            cipher.decrypt(owner, if (real) config!!.encryptedSecret else dummyEncryptedSecret)
        } catch (ex: Exception) {
            // Solo el tipo: nunca el secreto, el usuario ni el mensaje.
            log.log(System.Logger.Level.WARNING, "No se pudo descifrar el secreto TOTP: ${ex.javaClass.name}")
            null
        }
        val step = totp.verify(secret ?: dummySecret, code, now)
        if (real && secret != null && step != null) return totpRepo.markStepUsed(owner, step)
        totpRepo.markStepUsed(DUMMY_USER_ID, DUMMY_STEP) // afecta 0 filas: el usuario ficticio no existe
        return false
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

/** Usuario ficticio del camino constante: el UUID nulo, que nunca se asigna a un usuario real. */
val DUMMY_USER_ID: UUID = UUID(0L, 0L)
private const val DUMMY_STEP = 0L

/** Borra los desafíos vencidos o cerrados para que la tabla no crezca sin límite. */
class PurgeLoginChallengesUseCase(
    private val challenges: LoginChallengeRepository,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(): Int = tx.inTransaction { challenges.deleteClosed(clock.instant()) }
}
