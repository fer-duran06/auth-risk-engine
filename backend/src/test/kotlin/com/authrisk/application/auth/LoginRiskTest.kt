package com.authrisk.application.auth

import com.authrisk.application.auth.AuthFixture.Companion.PASSWORD
import com.authrisk.domain.risk.ChallengeRequiredException
import com.authrisk.domain.risk.LoginBlockedException
import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.RiskAssessment
import com.authrisk.domain.risk.RiskSignals
import com.authrisk.domain.risk.RiskZone
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
    fun `CHALLENGE con contraseña correcta no emite tokens`() {
        f.riskCounters.signals = challengeSignals

        assertFailsWith<ChallengeRequiredException> { f.login.execute(email, PASSWORD, IP) }

        assertEquals(0, f.refreshTokens.tokens.size)
        assertEquals(LoginOutcome.CHALLENGED, f.riskCounters.recorded.single().third)
        val attempt = f.loginAttempts.attempts.single()
        assertEquals(RiskZone.CHALLENGE, attempt.assessment.zone)
        assertEquals(false, attempt.success)
    }

    @Test
    fun `CHALLENGE con contraseña incorrecta responde como credenciales inválidas`() {
        f.riskCounters.signals = challengeSignals

        assertFailsWith<InvalidCredentialsException> { f.login.execute(email, "incorrecta-123", IP) }

        assertEquals(LoginOutcome.FAILURE, f.riskCounters.recorded.single().third)
    }

    @Test
    fun `fail-safe con contadores caídos exige verificación, nunca permite`() {
        f.riskCounters.failing = true

        assertFailsWith<ChallengeRequiredException> { f.login.execute(email, PASSWORD, IP) }
        assertFailsWith<InvalidCredentialsException> { f.login.execute(email, "incorrecta-123", IP) }

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
