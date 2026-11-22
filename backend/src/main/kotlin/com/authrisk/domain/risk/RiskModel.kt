package com.authrisk.domain.risk

import java.time.Instant
import java.util.UUID

/** Zona de decisión para un intento de login. */
enum class RiskZone { ALLOW, CHALLENGE, BLOCK }

/**
 * Contadores leídos antes de evaluar el intento actual (no lo incluyen).
 * Las ventanas de tiempo de cada contador las define el adaptador que los mantiene.
 */
data class RiskSignals(
    /** Intentos previos desde la IP en la ventana de velocidad. */
    val ipAttempts: Long = 0,
    /** Emails distintos intentados desde la IP en su ventana. */
    val distinctEmailsForIp: Long = 0,
    /** Fallos previos contra el email en su ventana. */
    val accountFailures: Long = 0,
    /** Intentos evaluados (no bloqueados) en todo el sistema en la ventana global. */
    val globalAttempts: Long = 0,
    /** Fallos de contraseña en todo el sistema en la ventana global. */
    val globalFailures: Long = 0,
)

/**
 * Resultado de la evaluación. [score] es null solo en el fail-safe, cuando no hubo contadores
 * con qué calcularlo. Nunca se devuelve al cliente.
 */
data class RiskAssessment(val score: Int?, val zone: RiskZone, val triggeredRules: List<String>) {
    companion object {
        const val COUNTERS_UNAVAILABLE = "COUNTERS_UNAVAILABLE"
        const val RISK_DISABLED = "RISK_DISABLED"

        /** Verificación 2FA con un challengeId inexistente, vencido, ya usado o sin intentos. */
        const val CHALLENGE_INVALID = "CHALLENGE_INVALID"

        /** Contadores no disponibles: se exige verificación adicional, nunca se permite directamente. */
        fun failSafe() = RiskAssessment(null, RiskZone.CHALLENGE, listOf(COUNTERS_UNAVAILABLE))

        /** Motor desactivado (solo para comparar en demos): se permite sin evaluar y queda marcado. */
        fun disabled() = RiskAssessment(0, RiskZone.ALLOW, listOf(RISK_DISABLED))
    }
}

/** Qué pasó con el intento; decide qué contadores se incrementan. */
enum class LoginOutcome {
    /** Contraseña correcta y tokens emitidos. */
    SUCCESS,

    /** Usuario inexistente o contraseña incorrecta. */
    FAILURE,

    /** Contraseña correcta, pero se exigió el segundo factor (sin tokens todavía). */
    CHALLENGED,

    /** Bloqueado antes de verificar la contraseña. */
    BLOCKED,
    ;

    /** Los bloqueados no cuentan como fallo de la cuenta: un atacante no debe poder castigar cuentas ajenas. */
    val countsAsAccountFailure: Boolean get() = this == FAILURE

    /** Los bloqueados quedan fuera de la tasa global para no generar un bucle que se alimente solo. */
    val countsGlobally: Boolean get() = this != BLOCKED

    val countsAsGlobalFailure: Boolean get() = this == FAILURE
}

/** Paso del login al que corresponde un intento. */
enum class LoginStage {
    /** Email y contraseña (POST /api/auth/login). */
    PASSWORD,

    /** Código TOTP de un desafío (POST /api/auth/2fa/verify). */
    TOTP,
}

/** Registro de un intento con su evaluación, para el administrador. */
data class LoginAttempt(
    val id: UUID,
    /** Email tal como se intentó (normalizado); puede no corresponder a ninguna cuenta. */
    val email: String,
    val ip: String,
    val assessment: RiskAssessment,
    val outcome: LoginOutcome,
    val createdAt: Instant,
    val stage: LoginStage = LoginStage.PASSWORD,
) {
    val success: Boolean get() = outcome == LoginOutcome.SUCCESS
}
