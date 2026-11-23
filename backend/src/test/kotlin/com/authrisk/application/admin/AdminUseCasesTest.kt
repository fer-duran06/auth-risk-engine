package com.authrisk.application.admin

import com.authrisk.application.auth.MutableClock
import com.authrisk.domain.risk.RiskZone
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AdminUseCasesTest {
    /** Anota lo que recibe el adaptador: los casos de uso solo deben pasarle valores validados. */
    private class RecordingQueries : AdminQueries {
        var lastFilter: EventFilter? = null
        var lastPage: Pair<Int, Int>? = null
        var lastSince: Instant? = null
        var lastLimit: Int? = null

        override fun stats(since: Instant, until: Instant, topIps: Int): AdminStats {
            lastSince = since
            lastLimit = topIps
            return AdminStats(since, until, 0, ZoneCounts(0, 0, 0), 0, 0, 0, 0, emptyList())
        }

        override fun events(filter: EventFilter, page: Int, size: Int): EventPage {
            lastFilter = filter
            lastPage = page to size
            return EventPage(emptyList(), page, size, 0)
        }

        override fun recentIps(since: Instant, limit: Int): List<IpActivity> {
            lastSince = since
            lastLimit = limit
            return emptyList()
        }
    }

    private val queries = RecordingQueries()
    private val clock = MutableClock()

    @Test
    fun `stats usa las últimas 24 h y el top 10`() {
        val stats = GetAdminStatsUseCase(queries, clock).execute()
        assertEquals(clock.instant().minus(Duration.ofHours(24)), stats.since)
        assertEquals(clock.instant(), stats.until)
        assertEquals(10, queries.lastLimit)
    }

    @Test
    fun `events normaliza filtros vacíos y la zona`() {
        val events = ListAdminEventsUseCase(queries)
        events.execute(" ", "", 0, 25)
        assertEquals(EventFilter(null, null), queries.lastFilter)

        events.execute(" block ", " 198.51.100.1 ", 3, 100)
        assertEquals(EventFilter(RiskZone.BLOCK, "198.51.100.1"), queries.lastFilter)
        assertEquals(3 to 100, queries.lastPage)
    }

    @Test
    fun `events rechaza parámetros fuera de rango sin consultar`() {
        val events = ListAdminEventsUseCase(queries)
        listOf<() -> Unit>(
            { events.execute(null, null, -1, 25) },
            { events.execute(null, null, 10_001, 25) },
            { events.execute(null, null, 0, 0) },
            { events.execute(null, null, 0, 101) },
            { events.execute("PELIGRO", null, 0, 25) },
            { events.execute(null, "1".repeat(46), 0, 25) },
        ).forEach { assertFailsWith<InvalidAdminQueryException> { it() } }
        assertNull(queries.lastFilter)
    }

    @Test
    fun `ips acota el límite y usa la ventana de 24 h`() {
        val ips = ListRecentIpsUseCase(queries, clock)
        ips.execute(100)
        assertEquals(clock.instant().minus(Duration.ofHours(24)), queries.lastSince)
        assertFailsWith<InvalidAdminQueryException> { ips.execute(0) }
        assertFailsWith<InvalidAdminQueryException> { ips.execute(101) }
    }

    @Test
    fun `EventPage calcula el total de páginas`() {
        assertEquals(0, EventPage(emptyList(), 0, 25, 0).totalPages)
        assertEquals(1, EventPage(emptyList(), 0, 25, 25).totalPages)
        assertEquals(3, EventPage(emptyList(), 0, 3, 8).totalPages)
    }
}
