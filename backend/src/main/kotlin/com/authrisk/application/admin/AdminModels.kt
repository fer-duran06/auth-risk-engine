package com.authrisk.application.admin

import com.authrisk.domain.risk.LoginStage
import com.authrisk.domain.risk.RiskZone
import java.time.Instant
import java.util.UUID

// Modelos de lectura del panel de administración. Incluyen score y reglas: solo se exponen bajo /api/admin.

data class ZoneCounts(val allow: Long, val challenge: Long, val block: Long)

data class IpCount(val ip: String, val attempts: Long)

/**
 * Resumen de una ventana. Los intentos de login (stage PASSWORD) y las verificaciones 2FA
 * (stage TOTP) se cuentan por separado para que un verify no figure como un segundo login.
 */
data class AdminStats(
    val since: Instant,
    val until: Instant,
    /** Intentos de login (contraseña). */
    val loginAttempts: Long,
    val byZone: ZoneCounts,
    /** Intentos de login que no emitieron tokens (incorrectos, desafiados o bloqueados). */
    val loginWithoutTokens: Long,
    /** Sesiones abiertas: login en ALLOW o verificación 2FA exitosa. */
    val sessionsIssued: Long,
    val totpSuccesses: Long,
    val totpFailures: Long,
    val topIps: List<IpCount>,
)

data class AdminEvent(
    val id: UUID,
    val createdAt: Instant,
    val email: String,
    val ip: String,
    val stage: LoginStage,
    val zone: RiskZone,
    /** null solo en el fail-safe o en verificaciones de un desafío desconocido. */
    val score: Int?,
    val triggeredRules: List<String>,
    val success: Boolean,
)

data class EventFilter(val zone: RiskZone?, val ip: String?)

data class EventPage(val items: List<AdminEvent>, val page: Int, val size: Int, val total: Long) {
    val totalPages: Long get() = if (total == 0L) 0 else (total + size - 1) / size
}

/** Actividad reciente de una IP (solo intentos de login). La zona es la de su último intento, no un bloqueo vigente. */
data class IpActivity(
    val ip: String,
    val attempts: Long,
    val failures: Long,
    val lastSeen: Instant,
    val lastZone: RiskZone,
    val lastScore: Int?,
)

data class RuleConfig(
    val id: String,
    val weight: Int,
    /** Umbral de conteo, o proporción en la regla de tasa global. */
    val threshold: Double,
    /** Mínimo de intentos para evaluar (solo en la regla de tasa global). */
    val minAttempts: Int?,
    val windowSeconds: Long,
)

data class RiskConfigView(
    val enabled: Boolean,
    val challengeThreshold: Int,
    val blockThreshold: Int,
    val maxScore: Int,
    val rules: List<RuleConfig>,
)

/** Parámetros de consulta fuera de rango. Mensaje genérico, sin repetir lo recibido. */
class InvalidAdminQueryException : RuntimeException("Parámetros de consulta inválidos")

/** Consultas de solo lectura sobre login_attempts. Cada método es una única consulta agregada. */
interface AdminQueries {
    fun stats(since: Instant, until: Instant, topIps: Int): AdminStats
    fun events(filter: EventFilter, page: Int, size: Int): EventPage
    fun recentIps(since: Instant, limit: Int): List<IpActivity>
}
