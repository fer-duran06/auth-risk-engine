package com.authrisk.domain.port

import com.authrisk.domain.risk.LoginAttempt

/** Historial de intentos de login con su evaluación de riesgo (solo para administración). */
interface LoginAttemptRepository {
    fun save(attempt: LoginAttempt)
}
