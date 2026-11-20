package com.authrisk.domain.risk

/** Regla de riesgo: si se dispara, suma [weight] al score. Debe ser pura y sin estado. */
interface RiskRule {
    /** Identificador estable que se guarda en `login_attempts.triggered_rules`. */
    val id: String
    val weight: Int

    fun triggers(signals: RiskSignals): Boolean
}

/** Validación común del peso de una regla. */
internal fun requireWeight(weight: Int) =
    require(weight in 0..RiskPolicy.MAX_SCORE) { "Peso fuera de rango: $weight" }
