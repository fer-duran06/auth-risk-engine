package com.authrisk.domain.risk

/** Suma los pesos de las reglas disparadas, acota el score a 100 y asigna la zona. */
class RiskEngine(private val rules: List<RiskRule>, private val policy: RiskPolicy) {
    init {
        require(rules.map { it.id }.toSet().size == rules.size) { "Hay reglas con id repetido" }
    }

    fun assess(signals: RiskSignals): RiskAssessment {
        val triggered = rules.filter { it.triggers(signals) }
        val score = triggered.sumOf { it.weight }.coerceAtMost(RiskPolicy.MAX_SCORE)
        return RiskAssessment(score, policy.zoneFor(score), triggered.map { it.id })
    }
}
