package com.authrisk.application.auth

import com.authrisk.application.auth.AuthFixture.Companion.PASSWORD
import com.authrisk.domain.risk.ChallengeRequiredException
import com.authrisk.domain.risk.LoginBlockedException
import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.RiskAssessment
import com.authrisk.domain.risk.RiskSignals
import com.authrisk.domain.risk.RiskZone
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.InvalidCredentialsException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Integración del motor de riesgo en el login, con contadores falsos. */
class LoginRiskTest {
    private val f = AuthFixture()
    private val email = "ana@example.com"

    // Con los pesos por defecto: 30 (CHALLENGE) y 75 (BLOCK).
    private val challengeSignals = RiskSignals(ipAttempts = 10)
    private val blockSignals = RiskSignals(ipAttempts = 10, distinctEmailsForIp = 5, accountFailures = 5)

    @BeforeTest
    fun setUp() {
        f.register.execute(email, PASSWORD)
    }

    @Test
    fun `ALLOW emite tokens y registra el intento exitoso`() {
        f.login.execute(email, PASSWORD, IP)

        assertEquals(1, f.refreshTokens.tokens.size)
        assertEquals(listOf(Triple(IP, email, LoginOutcome.SUCCESS)), f.riskCounters.recorded)
        val attempt = f.loginAttempts.attempts.single()
        assertEquals(RiskAssessment(0, RiskZone.ALLOW, emptyList()), attempt.assessment)
        assertTrue(attempt.success)
        assertEquals(IP, attempt.ip)
    }

    @Test
    fun `BLOCK rechaza sin verificar la contraseña, exista o no el email`() {
        f.riskCounters.signals = blockSignals
        val matchesBefore = f.hasher.matchCalls

        val existing = assertFailsWith<LoginBlockedException> { f.login.execute(email, PASSWORD, IP) }
        val missing = assertFailsWith<LoginBlockedException> { f.login.execute("nadie@example.com", PASSWORD, IP) }

        assertEquals(existing.message, missing.message)
        assertEquals(matchesBefore, f.hasher.matchCalls)
        assertEquals(0, f.refreshTokens.tokens.size)
        assertTrue(f.riskCounters.recorded.all { it.third == LoginOutcome.BLOCKED })
        val attempt = f.loginAttempts.attempts.first()
        assertEquals(RiskZone.BLOCK, attempt.assessment.zone)
        assertEquals(75, attempt.assessment.score)
        assertEquals(listOf("IP_VELOCITY", "DISTINCT_USERS_PER_IP", "ACCOUNT_FAILURES"), attempt.assessment.triggeredRules)
    }

    @Test
    fun `CHALLENGE con contraseña correcta no emite tokens y guarda un desafío completable`() {
        f.riskCounters.signals = challengeSignals

        val ex = assertFailsWith<ChallengeRequiredException> { f.login.execute(email, PASSWORD, IP) }

        assertEquals(0, f.refreshTokens.tokens.size)
        assertEquals(LoginOutcome.CHALLENGED, f.riskCounters.recorded.single().third)
        val attempt = f.loginAttempts.attempts.single()
        assertEquals(RiskZone.CHALLENGE, attempt.assessment.zone)
        assertEquals(false, attempt.success)
        val challenge = f.challenges.rows.values.single()
        assertEquals(f.challengeIds.hash(ex.challengeId), challenge.idHash)
        assertTrue(challenge.passwordOk)
        assertEquals(f.users.findByEmail(Email.of(email))!!.id, challenge.userId)
        assertEquals(f.clock.instant().plus(f.challengeTtl), challenge.expiresAt)
        assertEquals(attempt.assessment, challenge.assessment)
        val notification = f.notifications.rows.single()
        assertEquals(f.users.findByEmail(Email.of(email))!!.id, notification.userId)
        assertTrue(notification.unread)
    }

    @Test
    fun `CHALLENGE responde igual con contraseña incorrecta o email inexistente`() {
        f.riskCounters.signals = challengeSignals
        val matchesBefore = f.hasher.matchCalls

        val ok = assertFailsWith<ChallengeRequiredException> { f.login.execute(email, PASSWORD, IP) }
        val wrong = assertFailsWith<ChallengeRequiredException> { f.login.execute(email, "incorrecta-123", IP) }
        val missing = assertFailsWith<ChallengeRequiredException> { f.login.execute("nadie@example.com", PASSWORD, IP) }

        // Mismo tipo y mensaje; solo cambia el id aleatorio. Argon2 se ejecutó en los tres casos.
        assertEquals(setOf(ok.message), setOf(wrong.message, missing.message))
        assertEquals(3, setOf(ok.challengeId, wrong.challengeId, missing.challengeId).size)
        assertEquals(matchesBefore + 3, f.hasher.matchCalls)
        assertEquals(listOf(true, false, false), f.challenges.rows.values.map { it.passwordOk })
        assertNull(f.challenges.rows.values.last().userId)
        // Internamente los fallos siguen contando como fallos.
        assertEquals(
            listOf(LoginOutcome.CHALLENGED, LoginOutcome.FAILURE, LoginOutcome.FAILURE),
            f.riskCounters.recorded.map { it.third },
        )
        assertEquals(0, f.refreshTokens.tokens.size)
        // Solo las dos primeras (contraseña correcta e incorrecta) son del email existente;
        // el tercer intento, contra un email inexistente, no genera notificación.
        val userId = f.users.findByEmail(Email.of(email))!!.id
        assertEquals(2, f.notifications.rows.size)
        assertTrue(f.notifications.rows.all { it.userId == userId })
    }

    @Test
    fun `si no se puede guardar el desafío la respuesta no cambia y nunca habrá tokens`() {
        f.riskCounters.signals = challengeSignals
        f.challenges.failing = true

        val ex = assertFailsWith<ChallengeRequiredException> { f.login.execute(email, PASSWORD, IP) }

        assertTrue(ex.challengeId.isNotBlank())
        assertTrue(f.challenges.rows.isEmpty())
        assertEquals(0, f.refreshTokens.tokens.size)
    }

    @Test
    fun `fail-safe con contadores caídos exige verificación, nunca permite`() {
        f.riskCounters.failing = true

        assertFailsWith<ChallengeRequiredException> { f.login.execute(email, PASSWORD, IP) }
        assertFailsWith<ChallengeRequiredException> { f.login.execute(email, "incorrecta-123", IP) }

        assertEquals(0, f.refreshTokens.tokens.size)
        val attempt = f.loginAttempts.attempts.first()
        assertNull(attempt.assessment.score)
        assertEquals(RiskZone.CHALLENGE, attempt.assessment.zone)
        assertEquals(listOf(RiskAssessment.COUNTERS_UNAVAILABLE), attempt.assessment.triggeredRules)
    }

    @Test
    fun `el intento se registra con el email normalizado, aunque sea inválido`() {
        assertFailsWith<InvalidCredentialsException> { f.login.execute("  ANA@Example.com ", "incorrecta-123", IP) }
        assertFailsWith<InvalidCredentialsException> { f.login.execute(" No-Es-Email ", PASSWORD, IP) }

        assertEquals(listOf(email, "no-es-email"), f.loginAttempts.attempts.map { it.email })
        assertEquals(listOf(email, "no-es-email"), f.riskCounters.recorded.map { it.second })
    }
}
