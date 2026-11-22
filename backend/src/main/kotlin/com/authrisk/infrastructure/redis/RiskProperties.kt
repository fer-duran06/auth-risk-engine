package com.authrisk.infrastructure.redis

import com.authrisk.domain.risk.RiskEngine
import com.authrisk.domain.risk.RiskPolicy
import com.authrisk.domain.risk.rules.AccountFailuresRule
import com.authrisk.domain.risk.rules.DistinctUsersPerIpRule
import com.authrisk.domain.risk.rules.GlobalFailureRateRule
import com.authrisk.domain.risk.rules.IpVelocityRule
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuración del motor de riesgo (prefijo `app.risk`): umbrales de zona, y peso, umbral y
 * ventana de cada regla. Los valores inválidos hacen fallar el arranque (validación del dominio).
 * [enabled] en false desactiva la evaluación; solo se admite con `app.demo-mode=true`.
 */
@ConfigurationProperties(prefix = "app.risk")
data class RiskProperties(
    val enabled: Boolean = true,
    val thresholds: Thresholds = Thresholds(),
    val rules: Rules = Rules(),
) {
    data class Thresholds(val challenge: Int = 30, val block: Int = 70)

    data class Rules(
        val ipVelocity: CountRule = CountRule(30, 10, Duration.ofMinutes(1)),
        val distinctUsersPerIp: CountRule = CountRule(25, 5, Duration.ofMinutes(10)),
        val accountFailures: CountRule = CountRule(20, 5, Duration.ofMinutes(15)),
        val globalFailureRate: RateRule = RateRule(),
    )

    data class CountRule(val weight: Int, val threshold: Int, val window: Duration) {
        init {
            require(!window.isNegative && !window.isZero) { "La ventana debe ser positiva" }
        }
    }

    /** La ventana global se cuenta en buckets de un minuto, así que se exige al menos 1 min. */
    data class RateRule(
        val weight: Int = 15,
        val ratio: Double = 0.5,
        val minAttempts: Int = 20,
        val window: Duration = Duration.ofMinutes(5),
    ) {
        init {
            require(window >= Duration.ofMinutes(1)) { "La ventana global debe ser de al menos 1 minuto" }
        }
    }

    /**
     * Devuelve si el motor está activo. Desactivarlo fuera del modo demo impide el arranque:
     * así un error de configuración nunca deja el login de un entorno real sin protección.
     */
    fun effectiveEnabled(demoMode: Boolean): Boolean {
        check(enabled || demoMode) { "app.risk.enabled=false solo se permite con app.demo-mode=true" }
        return enabled
    }

    fun toEngine() = RiskEngine(
        rules = listOf(
            IpVelocityRule(rules.ipVelocity.weight, rules.ipVelocity.threshold),
            DistinctUsersPerIpRule(rules.distinctUsersPerIp.weight, rules.distinctUsersPerIp.threshold),
            AccountFailuresRule(rules.accountFailures.weight, rules.accountFailures.threshold),
            GlobalFailureRateRule(rules.globalFailureRate.weight, rules.globalFailureRate.ratio, rules.globalFailureRate.minAttempts),
        ),
        policy = RiskPolicy(thresholds.challenge, thresholds.block),
    )
}
