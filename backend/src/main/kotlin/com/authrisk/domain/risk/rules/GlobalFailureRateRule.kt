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

/**
 * Muchas cuentas distintas con fallo reciente en toda la plataforma, sin importar la IP de origen.
 * Cubre el hueco que dejan las reglas por IP ([IpVelocityRule], [DistinctUsersPerIpRule]): un
 * credential stuffing repartido entre muchas IPs (uno o pocos intentos por IP) nunca hace que esas
 * reglas se disparen, porque cada IP individual parece normal. Vista en conjunto, en cambio, la
 * cantidad de cuentas distintas fallando al mismo tiempo es anómala.
 */
class GlobalDistinctAccountVelocityRule(override val weight: Int = 20, private val threshold: Int = 20) : RiskRule {
    init {
        requireWeight(weight)
        require(threshold >= 1) { "El umbral debe ser >= 1" }
    }

    override val id = "GLOBAL_DISTINCT_ACCOUNT_VELOCITY"
    override fun triggers(signals: RiskSignals) = signals.globalDistinctFailingAccounts >= threshold
}
