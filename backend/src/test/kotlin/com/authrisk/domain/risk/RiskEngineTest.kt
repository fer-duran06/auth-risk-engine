package com.authrisk.domain.risk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RiskEngineTest {

    /** Regla de prueba con resultado fijo. */
    private class FixedRule(override val id: String, override val weight: Int, private val fires: Boolean = true) : RiskRule {
        override fun triggers(signals: RiskSignals) = fires
    }

    private fun engine(vararg rules: RiskRule) = RiskEngine(rules.toList(), RiskPolicy(30, 70))
    private fun scoreOf(vararg weights: Int) = engine(*weights.mapIndexed { i, w -> FixedRule("R$i", w) }.toTypedArray())
        .assess(RiskSignals())

    @Test
    fun `sin reglas disparadas es ALLOW con score 0`() {
        val result = engine(FixedRule("A", 50, fires = false)).assess(RiskSignals())
        assertEquals(RiskAssessment(0, RiskZone.ALLOW, emptyList()), result)
    }

    @Test
    fun `bordes de zona`() {
        assertEquals(RiskZone.ALLOW, scoreOf(29).zone)
        assertEquals(RiskZone.CHALLENGE, scoreOf(30).zone)
        assertEquals(RiskZone.CHALLENGE, scoreOf(69).zone)
        assertEquals(RiskZone.BLOCK, scoreOf(70).zone)
    }

    @Test
    fun `suma solo las reglas disparadas y las informa en orden`() {
        val result = engine(FixedRule("A", 30), FixedRule("B", 25, fires = false), FixedRule("C", 15)).assess(RiskSignals())
        assertEquals(45, result.score)
        assertEquals(listOf("A", "C"), result.triggeredRules)
    }

    @Test
    fun `el score se acota a 100`() {
        val result = scoreOf(60, 60)
        assertEquals(100, result.score)
        assertEquals(RiskZone.BLOCK, result.zone)
    }

    @Test
    fun `pesos por defecto escalan una ráfaga de credential stuffing`() {
        val defaults = RiskEngine(
            listOf(
                com.authrisk.domain.risk.rules.IpVelocityRule(),
                com.authrisk.domain.risk.rules.DistinctUsersPerIpRule(),
                com.authrisk.domain.risk.rules.AccountFailuresRule(),
                com.authrisk.domain.risk.rules.GlobalFailureRateRule(),
            ),
            RiskPolicy(),
        )
        assertEquals(25, defaults.assess(RiskSignals(ipAttempts = 5, distinctEmailsForIp = 5)).score)
        assertEquals(RiskZone.CHALLENGE, defaults.assess(RiskSignals(ipAttempts = 10, distinctEmailsForIp = 10)).zone)
        val all = defaults.assess(RiskSignals(10, 10, 5, 20, 20))
        assertEquals(90, all.score)
        assertEquals(RiskZone.BLOCK, all.zone)
    }

    @Test
    fun `configuraciones inválidas se rechazan`() {
        assertFailsWith<IllegalArgumentException> { RiskPolicy(70, 30) }
        assertFailsWith<IllegalArgumentException> { RiskPolicy(0, 70) }
        assertFailsWith<IllegalArgumentException> { RiskPolicy(30, 101) }
        assertFailsWith<IllegalArgumentException> { engine(FixedRule("A", 10), FixedRule("A", 20)) }
    }

    @Test
    fun `el fail-safe es CHALLENGE sin score`() {
        val failSafe = RiskAssessment.failSafe()
        assertEquals(RiskZone.CHALLENGE, failSafe.zone)
        assertEquals(null, failSafe.score)
        assertEquals(listOf(RiskAssessment.COUNTERS_UNAVAILABLE), failSafe.triggeredRules)
    }
}
