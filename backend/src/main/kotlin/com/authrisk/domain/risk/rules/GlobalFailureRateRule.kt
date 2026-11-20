package com.authrisk.domain.risk.rules

import com.authrisk.domain.risk.RiskRule
import com.authrisk.domain.risk.RiskSignals
import com.authrisk.domain.risk.requireWeight

/**
 * Proporción de fallos de todo el sistema alta: ataque distribuido en curso.
 * Exige [minAttempts] para no dispararse por ruido con poco tráfico.
 */
class GlobalFailureRateRule(
    override val weight: Int = 15,
    private val ratio: Double = 0.5,
    private val minAttempts: Int = 20,
) : RiskRule {
    init {
        requireWeight(weight)
        require(ratio > 0.0 && ratio <= 1.0) { "La proporción debe estar en (0, 1]" }
        require(minAttempts >= 1) { "El mínimo de intentos debe ser >= 1" }
    }

    override val id = "GLOBAL_FAILURE_RATE"

    override fun triggers(signals: RiskSignals): Boolean =
        signals.globalAttempts >= minAttempts &&
            signals.globalFailures.toDouble() / signals.globalAttempts >= ratio
}
