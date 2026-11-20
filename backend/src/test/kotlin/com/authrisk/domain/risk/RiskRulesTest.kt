package com.authrisk.domain.risk

import com.authrisk.domain.risk.rules.AccountFailuresRule
import com.authrisk.domain.risk.rules.DistinctUsersPerIpRule
import com.authrisk.domain.risk.rules.GlobalFailureRateRule
import com.authrisk.domain.risk.rules.IpVelocityRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Cada regla se dispara justo en su umbral y no un paso antes. */
class RiskRulesTest {

    @Test
    fun `velocidad por IP`() {
        val rule = IpVelocityRule(weight = 30, threshold = 10)
        assertFalse(rule.triggers(RiskSignals(ipAttempts = 9)))
        assertTrue(rule.triggers(RiskSignals(ipAttempts = 10)))
        assertEquals(30, rule.weight)
        assertEquals("IP_VELOCITY", rule.id)
    }

    @Test
    fun `usuarios distintos por IP`() {
        val rule = DistinctUsersPerIpRule(weight = 25, threshold = 5)
        assertFalse(rule.triggers(RiskSignals(distinctEmailsForIp = 4)))
        assertTrue(rule.triggers(RiskSignals(distinctEmailsForIp = 5)))
        // Solo mira su propio contador.
        assertFalse(rule.triggers(RiskSignals(ipAttempts = 100, accountFailures = 100)))
    }

    @Test
    fun `fallos por cuenta`() {
        val rule = AccountFailuresRule(weight = 20, threshold = 5)
        assertFalse(rule.triggers(RiskSignals(accountFailures = 4)))
        assertTrue(rule.triggers(RiskSignals(accountFailures = 5)))
    }

    @Test
    fun `tasa global de fallos exige volumen mínimo y proporción`() {
        val rule = GlobalFailureRateRule(weight = 15, ratio = 0.5, minAttempts = 20)
        // 100 % de fallos pero poco tráfico: no se dispara.
        assertFalse(rule.triggers(RiskSignals(globalAttempts = 19, globalFailures = 19)))
        assertFalse(rule.triggers(RiskSignals(globalAttempts = 20, globalFailures = 9)))
        assertTrue(rule.triggers(RiskSignals(globalAttempts = 20, globalFailures = 10)))
        assertFalse(rule.triggers(RiskSignals()))
    }

    @Test
    fun `parámetros inválidos se rechazan`() {
        assertFailsWith<IllegalArgumentException> { IpVelocityRule(weight = -1) }
        assertFailsWith<IllegalArgumentException> { IpVelocityRule(weight = 101) }
        assertFailsWith<IllegalArgumentException> { DistinctUsersPerIpRule(threshold = 0) }
        assertFailsWith<IllegalArgumentException> { GlobalFailureRateRule(ratio = 0.0) }
        assertFailsWith<IllegalArgumentException> { GlobalFailureRateRule(ratio = 1.5) }
        assertFailsWith<IllegalArgumentException> { GlobalFailureRateRule(minAttempts = 0) }
    }

    @Test
    fun `los bloqueados no cuentan como fallo de cuenta ni en la tasa global`() {
        assertFalse(LoginOutcome.BLOCKED.countsAsAccountFailure)
        assertFalse(LoginOutcome.BLOCKED.countsGlobally)
        assertTrue(LoginOutcome.FAILURE.countsAsAccountFailure)
        assertTrue(LoginOutcome.FAILURE.countsAsGlobalFailure)
        assertTrue(LoginOutcome.CHALLENGED.countsGlobally)
        assertFalse(LoginOutcome.CHALLENGED.countsAsGlobalFailure)
    }
}
