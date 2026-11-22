package com.authrisk.application.auth

import com.authrisk.application.auth.AuthFixture.Companion.PASSWORD
import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.RiskAssessment
import com.authrisk.domain.risk.RiskSignals
import com.authrisk.domain.risk.RiskZone
import com.authrisk.domain.user.InvalidCredentialsException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Motor desactivado (app.risk.enabled=false): base de comparación del simulador. */
class LoginRiskDisabledTest {
    private val f = AuthFixture(riskEnabled = false).apply {
        // Señales que con el motor activo darían BLOCK (score 90).
        riskCounters.signals = RiskSignals(ipAttempts = 10, distinctEmailsForIp = 5, accountFailures = 5, globalAttempts = 20, globalFailures = 20)
    }
    private val email = "ana@example.com"

    @Test
    fun `con el motor desactivado se permite el login aunque las señales den BLOCK`() {
        f.register.execute(email, PASSWORD)

        f.login.execute(email, PASSWORD, IP)

        assertEquals(1, f.refreshTokens.tokens.size)
        val attempt = f.loginAttempts.attempts.single()
        assertEquals(RiskAssessment(0, RiskZone.ALLOW, listOf(RiskAssessment.RISK_DISABLED)), attempt.assessment)
        assertEquals(LoginOutcome.SUCCESS, attempt.outcome)
    }

    @Test
    fun `con el motor desactivado no se leen ni escriben contadores`() {
        f.register.execute(email, PASSWORD)

        f.login.execute(email, PASSWORD, IP)
        assertFailsWith<InvalidCredentialsException> { f.login.execute(email, "incorrecta-1234", IP) }

        assertEquals(0, f.riskCounters.reads)
        assertTrue(f.riskCounters.recorded.isEmpty())
        // El intento fallido igual queda registrado para el administrador.
        assertEquals(listOf(LoginOutcome.SUCCESS, LoginOutcome.FAILURE), f.loginAttempts.attempts.map { it.outcome })
    }

    @Test
    fun `con el motor desactivado Redis caído no afecta`() {
        f.register.execute(email, PASSWORD)
        f.riskCounters.failing = true

        f.login.execute(email, PASSWORD, IP)

        assertEquals(RiskZone.ALLOW, f.loginAttempts.attempts.single().assessment.zone)
    }
}
