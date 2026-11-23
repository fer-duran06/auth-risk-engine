package com.authrisk.application.admin

import com.authrisk.domain.risk.RiskZone
import java.time.Clock
import java.time.Duration

/** Ventana de "actividad reciente" del panel. */
val ADMIN_WINDOW: Duration = Duration.ofHours(24)

class GetAdminStatsUseCase(private val queries: AdminQueries, private val clock: Clock) {
    fun execute(): AdminStats {
        val until = clock.instant()
        return queries.stats(until.minus(ADMIN_WINDOW), until, TOP_IPS)
    }

    companion object {
        const val TOP_IPS = 10
    }
}

/**
 * Eventos paginados (offset), más recientes primero. Valida los parámetros aquí para que el
 * adaptador solo reciba valores seguros: zona del enum, IP acotada y página/tamaño en rango.
 */
class ListAdminEventsUseCase(private val queries: AdminQueries) {
    fun execute(zone: String?, ip: String?, page: Int, size: Int): EventPage {
        if (page !in 0..MAX_PAGE || size !in 1..MAX_SIZE) throw InvalidAdminQueryException()
        val zoneFilter = zone?.takeIf { it.isNotBlank() }?.let { raw ->
            RiskZone.entries.firstOrNull { it.name == raw.trim().uppercase() } ?: throw InvalidAdminQueryException()
        }
        val ipFilter = ip?.trim()?.takeIf { it.isNotEmpty() }?.also {
            if (it.length > IP_MAX_LENGTH) throw InvalidAdminQueryException()
        }
        return queries.events(EventFilter(zoneFilter, ipFilter), page, size)
    }

    companion object {
        const val DEFAULT_SIZE = 25
        const val MAX_SIZE = 100

        /** Acota el OFFSET: más allá, conviene filtrar en vez de paginar. */
        const val MAX_PAGE = 10_000
        const val IP_MAX_LENGTH = 45
    }
}

class ListRecentIpsUseCase(private val queries: AdminQueries, private val clock: Clock) {
    fun execute(limit: Int): List<IpActivity> {
        if (limit !in 1..MAX_LIMIT) throw InvalidAdminQueryException()
        return queries.recentIps(clock.instant().minus(ADMIN_WINDOW), limit)
    }

    companion object {
        const val DEFAULT_LIMIT = 50
        const val MAX_LIMIT = 100
    }
}

/** Configuración efectiva del motor (solo lectura): se fija al arrancar. */
class GetRiskConfigUseCase(private val config: RiskConfigView) {
    fun execute(): RiskConfigView = config
}
