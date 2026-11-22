package com.authrisk.application.twofactor

import com.authrisk.application.auth.AuthFixture
import com.authrisk.application.auth.AuthFixture.Companion.PASSWORD
import com.authrisk.application.auth.IP
import com.authrisk.domain.risk.ChallengeRequiredException
import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.LoginStage
import com.authrisk.domain.risk.RiskAssessment
import com.authrisk.domain.risk.RiskSignals
import com.authrisk.domain.totp.Totp
import com.authrisk.domain.user.InvalidCredentialsException
import java.time.Duration
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VerifyLoginChallengeUseCaseTest {
    private val f = AuthFixture()
    private val email = "ana@example.com"
    private lateinit var userId: UUID
    private val secret = Totp.newSecret()

    @BeforeTest
    fun setUp() {
        f.register.execute(email, PASSWORD)
        userId = f.users.users.keys.single()
        f.riskCounters.signals = RiskSignals(ipAttempts = 10) // score 30: CHALLENGE
    }

    private fun enable2fa(id: UUID = userId) {
        f.totpRepo.savePending(id, f.cipher.encrypt(id, secret), f.clock.instant())
        f.totpRepo.enable(id, f.clock.instant())
    }

    private fun code(offset: Long = 0) = f.totp.generate(secret, f.totp.stepAt(f.clock.instant()) + offset)

    private fun challenge(pwd: String = PASSWORD, mail: String = email): String =
        assertFailsWith<ChallengeRequiredException> { f.login.execute(mail, pwd, IP) }.challengeId

    private fun rejected(challengeId: String, code: String) =
        assertFailsWith<InvalidCredentialsException> { f.verify.execute(challengeId, code, IP) }

    private fun totpAttempts() = f.loginAttempts.attempts.filter { it.stage == LoginStage.TOTP }

    @Test
    fun `contraseña correcta, 2FA activo y código válido emiten tokens y cierran el desafío`() {
        enable2fa()
        val id = challenge()

        val tokens = f.verify.execute(id, code(), IP)

        assertEquals("jwt-for-$userId", tokens.accessToken.value)
        assertEquals(1, f.refreshTokens.tokens.size)
        assertNotNull(f.challenges.rows.values.single().consumedAt)
        val attempt = totpAttempts().single()
        assertTrue(attempt.success)
        assertEquals(email, attempt.email)
        assertEquals(30, attempt.assessment.score)
        // El mismo challengeId no sirve dos veces, ni con un código nuevo.
        f.clock.advance(Duration.ofSeconds(30))
        rejected(id, code())
    }

    @Test
    fun `código incorrecto falla y cuenta el intento`() {
        enable2fa()
        val id = challenge()
        val wrong = if (code() == "000000") "111111" else "000000"

        rejected(id, wrong)
        rejected(id, "abc")

        assertEquals(2, f.challenges.rows.values.single().attempts)
        assertNull(f.challenges.rows.values.single().consumedAt)
        assertEquals(listOf(LoginOutcome.FAILURE, LoginOutcome.FAILURE), totpAttempts().map { it.outcome })
        // Aún abierto: un código válido funciona.
        f.verify.execute(id, code(), IP)
    }

    @Test
    fun `un código ya usado no se acepta otra vez, ni en otro desafío`() {
        enable2fa()
        val reused = code()
        f.verify.execute(challenge(), reused, IP)

        rejected(challenge(), reused)
        // El código del paso anterior tampoco: el último paso usado es el actual.
        rejected(challenge(), code(offset = -1))
        assertEquals(1, f.refreshTokens.tokens.size)
    }

    @Test
    fun `contraseña incorrecta o email inexistente nunca emiten tokens aunque el código sea válido`() {
        enable2fa()
        rejected(challenge(pwd = "incorrecta-123"), code())
        rejected(challenge(mail = "nadie@example.com"), code())
        assertEquals(0, f.refreshTokens.tokens.size)
        // El código no se consumió: sigue sirviendo con la contraseña correcta.
        f.verify.execute(challenge(), code(), IP)
    }

    @Test
    fun `usuario sin 2FA activo no puede completar el desafío`() {
        val id = challenge()
        rejected(id, code())

        // Con el secreto pendiente (sin confirmar) tampoco.
        f.totpRepo.savePending(userId, f.cipher.encrypt(userId, secret), f.clock.instant())
        rejected(challenge(), code())
        assertEquals(0, f.refreshTokens.tokens.size)
    }

    @Test
    fun `desafío vencido se rechaza`() {
        enable2fa()
        val id = challenge()
        f.clock.advance(f.challengeTtl)

        rejected(id, code())

        assertEquals(0, f.challenges.rows.values.single().attempts)
        assertTrue(RiskAssessment.CHALLENGE_INVALID in totpAttempts().single().assessment.triggeredRules)
    }

    @Test
    fun `tras 5 intentos fallidos el desafío se invalida`() {
        enable2fa()
        val id = challenge()
        val wrong = if (code() == "000000") "111111" else "000000"
        repeat(f.maxVerifyAttempts) { rejected(id, wrong) }

        assertNotNull(f.challenges.rows.values.single().consumedAt)
        rejected(id, code())
        assertEquals(0, f.refreshTokens.tokens.size)
        assertEquals(f.maxVerifyAttempts, f.challenges.rows.values.single().attempts)
    }

    @Test
    fun `challengeId desconocido se rechaza y se registra`() {
        enable2fa()
        rejected("inventado", code())

        val attempt = totpAttempts().single()
        assertEquals("", attempt.email)
        assertEquals(listOf(RiskAssessment.CHALLENGE_INVALID), attempt.assessment.triggeredRules)
    }

    @Test
    fun `si el desafío no se pudo guardar el id devuelto nunca se completa`() {
        enable2fa()
        f.challenges.failing = true
        val id = challenge()
        f.challenges.failing = false

        rejected(id, code())
        assertEquals(0, f.refreshTokens.tokens.size)
    }

    @Test
    fun `la purga borra vencidos y cerrados, pero no los abiertos`() {
        enable2fa()
        val purge = PurgeLoginChallengesUseCase(f.challenges, f.tx, f.clock)
        f.verify.execute(challenge(), code(), IP) // cerrado
        f.challenges.rows.values.first().let { assertNotNull(it.consumedAt) }
        f.clock.advance(Duration.ofMinutes(1))
        challenge() // vence a los 5 min de ahora
        f.clock.advance(Duration.ofMinutes(4))
        val open = challenge()

        assertEquals(1, purge.execute())
        f.clock.advance(Duration.ofMinutes(1))
        assertEquals(1, purge.execute())
        assertEquals(listOf(f.challengeIds.hash(open)), f.challenges.rows.values.map { it.idHash })
    }
}
