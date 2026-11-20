package com.authrisk.domain.port

import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.RiskSignals

/**
 * Contadores de intentos por ventana de tiempo. Ambas operaciones lanzan
 * [com.authrisk.domain.risk.RiskCountersUnavailableException] si el almacén no responde.
 */
interface RiskCounters {
    /** Lee los contadores para la IP y el email (normalizado) antes de evaluar el intento. */
    fun read(ip: String, email: String): RiskSignals

    /** Registra el intento según su resultado. */
    fun record(ip: String, email: String, outcome: LoginOutcome)
}
