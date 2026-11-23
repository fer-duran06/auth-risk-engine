package com.authrisk.infrastructure.persistence

import com.authrisk.application.admin.AdminEvent
import com.authrisk.application.admin.AdminQueries
import com.authrisk.application.admin.AdminStats
import com.authrisk.application.admin.EventFilter
import com.authrisk.application.admin.EventPage
import com.authrisk.application.admin.IpActivity
import com.authrisk.application.admin.IpCount
import com.authrisk.application.admin.ZoneCounts
import com.authrisk.domain.risk.LoginStage
import com.authrisk.domain.risk.RiskZone
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Lecturas del panel con SQL parametrizado. Cada método hace una consulta agregada (o dos, con el
 * conteo de la paginación), sin N+1. Los filtros de tiempo usan idx_login_attempts_created_at y el
 * de IP, idx_login_attempts_ip. Los fragmentos WHERE son constantes; los valores van como parámetros.
 */
@Component
class JdbcAdminQueries(private val jdbc: NamedParameterJdbcTemplate) : AdminQueries {

    override fun stats(since: Instant, until: Instant, topIps: Int): AdminStats {
        val params = MapSqlParameterSource().addValue("since", ts(since)).addValue("until", ts(until))
        val totals = jdbc.queryForObject(
            """
            SELECT count(*) FILTER (WHERE stage = 'PASSWORD')                                 AS login_attempts,
                   count(*) FILTER (WHERE stage = 'PASSWORD' AND zone = 'ALLOW')              AS zone_allow,
                   count(*) FILTER (WHERE stage = 'PASSWORD' AND zone = 'CHALLENGE')          AS zone_challenge,
                   count(*) FILTER (WHERE stage = 'PASSWORD' AND zone = 'BLOCK')              AS zone_block,
                   count(*) FILTER (WHERE stage = 'PASSWORD' AND NOT success)                 AS login_without_tokens,
                   count(*) FILTER (WHERE success)                                            AS sessions_issued,
                   count(*) FILTER (WHERE stage = 'TOTP' AND success)                         AS totp_ok,
                   count(*) FILTER (WHERE stage = 'TOTP' AND NOT success)                     AS totp_fail
              FROM login_attempts
             WHERE created_at >= :since AND created_at < :until
            """,
            params,
        ) { rs, _ -> rs.totals() }!!

        val top = jdbc.query(
            """
            SELECT ip, count(*) AS attempts
              FROM login_attempts
             WHERE created_at >= :since AND created_at < :until AND stage = 'PASSWORD'
             GROUP BY ip
             ORDER BY attempts DESC, ip
             LIMIT :limit
            """,
            params.addValue("limit", topIps),
        ) { rs, _ -> IpCount(rs.getString("ip"), rs.getLong("attempts")) }

        return AdminStats(
            since = since,
            until = until,
            loginAttempts = totals.getValue("login_attempts"),
            byZone = ZoneCounts(totals.getValue("zone_allow"), totals.getValue("zone_challenge"), totals.getValue("zone_block")),
            loginWithoutTokens = totals.getValue("login_without_tokens"),
            sessionsIssued = totals.getValue("sessions_issued"),
            totpSuccesses = totals.getValue("totp_ok"),
            totpFailures = totals.getValue("totp_fail"),
            topIps = top,
        )
    }

    override fun events(filter: EventFilter, page: Int, size: Int): EventPage {
        val params = MapSqlParameterSource()
        val conditions = buildList {
            filter.zone?.let { add("zone = :zone"); params.addValue("zone", it.name) }
            filter.ip?.let { add("ip = :ip"); params.addValue("ip", it) }
        }
        val where = if (conditions.isEmpty()) "" else "WHERE " + conditions.joinToString(" AND ")

        val total = jdbc.queryForObject("SELECT count(*) FROM login_attempts $where", params, Long::class.java)!!
        val items = if (total == 0L) emptyList() else jdbc.query(
            """
            SELECT id, created_at, email, ip, stage, zone, score, triggered_rules, success
              FROM login_attempts $where
             ORDER BY created_at DESC, id
             LIMIT :limit OFFSET :offset
            """,
            params.addValue("limit", size).addValue("offset", page.toLong() * size),
        ) { rs, _ -> rs.event() }
        return EventPage(items, page, size, total)
    }

    override fun recentIps(since: Instant, limit: Int): List<IpActivity> = jdbc.query(
        """
        SELECT ip,
               count(*)                                        AS attempts,
               count(*) FILTER (WHERE NOT success)             AS failures,
               max(created_at)                                 AS last_seen,
               (array_agg(zone  ORDER BY created_at DESC))[1]  AS last_zone,
               (array_agg(score ORDER BY created_at DESC))[1]  AS last_score
          FROM login_attempts
         WHERE created_at >= :since AND stage = 'PASSWORD'
         GROUP BY ip
         ORDER BY last_seen DESC, ip
         LIMIT :limit
        """,
        MapSqlParameterSource().addValue("since", ts(since)).addValue("limit", limit),
    ) { rs, _ ->
        IpActivity(
            ip = rs.getString("ip"),
            attempts = rs.getLong("attempts"),
            failures = rs.getLong("failures"),
            lastSeen = rs.instant("last_seen"),
            lastZone = RiskZone.valueOf(rs.getString("last_zone")),
            lastScore = rs.intOrNull("last_score"),
        )
    }

    private fun ts(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)

    private fun ResultSet.instant(column: String): Instant = getObject(column, OffsetDateTime::class.java).toInstant()

    private fun ResultSet.intOrNull(column: String): Int? = getInt(column).takeUnless { wasNull() }

    private fun ResultSet.totals(): Map<String, Long> =
        (1..metaData.columnCount).associate { metaData.getColumnLabel(it) to getLong(it) }

    private fun ResultSet.event(): AdminEvent {
        @Suppress("UNCHECKED_CAST")
        val rules = (getArray("triggered_rules").array as Array<String>).toList()
        return AdminEvent(
            id = getObject("id", UUID::class.java),
            createdAt = instant("created_at"),
            email = getString("email"),
            ip = getString("ip"),
            stage = LoginStage.valueOf(getString("stage")),
            zone = RiskZone.valueOf(getString("zone")),
            score = intOrNull("score"),
            triggeredRules = rules,
            success = getBoolean("success"),
        )
    }
}
