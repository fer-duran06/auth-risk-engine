package com.authrisk.domain.risk.rules

import com.authrisk.domain.risk.RiskRule
import com.authrisk.domain.risk.RiskSignals
import com.authrisk.domain.risk.requireWeight

/** Muchos intentos desde la misma IP en una ventana corta. */
class IpVelocityRule(override val weight: Int = 30, private val threshold: Int = 10) : RiskRule {
    init {
        requireWeight(weight)
        require(threshold >= 1) { "El umbral debe ser >= 1" }
    }

    override val id = "IP_VELOCITY"
    override fun triggers(signals: RiskSignals) = signals.ipAttempts >= threshold
}

/** Una IP que prueba muchas cuentas distintas: patrón típico de credential stuffing. */
class DistinctUsersPerIpRule(override val weight: Int = 25, private val threshold: Int = 5) : RiskRule {
    init {
        requireWeight(weight)
        require(threshold >= 1) { "El umbral debe ser >= 1" }
    }

    override val id = "DISTINCT_USERS_PER_IP"
    override fun triggers(signals: RiskSignals) = signals.distinctEmailsForIp >= threshold
}

/** Muchos fallos recientes contra la misma cuenta, vengan de donde vengan. */
class AccountFailuresRule(override val weight: Int = 20, private val threshold: Int = 5) : RiskRule {
    init {
        requireWeight(weight)
        require(threshold >= 1) { "El umbral debe ser >= 1" }
    }

    override val id = "ACCOUNT_FAILURES"
    override fun triggers(signals: RiskSignals) = signals.accountFailures >= threshold
}
