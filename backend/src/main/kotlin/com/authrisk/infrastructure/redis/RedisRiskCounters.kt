package com.authrisk.infrastructure.redis

import com.authrisk.domain.port.RiskCounters
import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.RiskCountersUnavailableException
import com.authrisk.domain.risk.RiskSignals
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Clock
import java.util.HexFormat
import java.util.UUID

/**
 * Contadores de riesgo en Redis. Cada operación es un único script Lua (un viaje, atómico).
 *
 * - Por IP y por cuenta: sorted sets con el instante en ms como score (ventana deslizante).
 *   Los emails distintos por IP son miembros del set, así ZCOUNT da los distintos en la ventana.
 * - Global: un contador por minuto (INCR + EXPIRE); la ventana suma los últimos N buckets.
 * - El email nunca se guarda en claro: se usa su SHA-256.
 *
 * Cualquier error de Redis se registra (solo el tipo, sin IP ni email) y se traduce a
 * [RiskCountersUnavailableException] para que el caso de uso aplique el fail-safe.
 */
@Component
class RedisRiskCounters(
    private val redis: StringRedisTemplate,
    props: RiskProperties,
    private val clock: Clock,
) : RiskCounters {
    private val log = LoggerFactory.getLogger(javaClass)

    private val ipWindowMs = props.rules.ipVelocity.window.toMillis()
    private val distinctWindowMs = props.rules.distinctUsersPerIp.window.toMillis()
    private val accountWindowMs = props.rules.accountFailures.window.toMillis()
    private val globalBuckets = props.rules.globalFailureRate.window.toMinutes()
    // El bucket vive lo que dura la ventana más un minuto de margen.
    private val bucketTtlSeconds = (globalBuckets + 1) * 60

    override fun read(ip: String, email: String): RiskSignals = guarded {
        val now = clock.millis()
        val minute = now / MINUTE_MS
        val buckets = (minute - globalBuckets + 1..minute).toList()
        val keys = listOf(ipAttemptsKey(ip), ipEmailsKey(ip), accountFailuresKey(email)) +
            buckets.map(::globalAttemptsKey) + buckets.map(::globalFailuresKey)
        val r = redis.execute(
            READ_SCRIPT, keys,
            (now - ipWindowMs).toString(), (now - distinctWindowMs).toString(), (now - accountWindowMs).toString(),
            globalBuckets.toString(),
        )
        RiskSignals(r[0], r[1], r[2], r[3], r[4])
    }

    override fun record(ip: String, email: String, outcome: LoginOutcome) {
        guarded {
            val now = clock.millis()
            val minute = now / MINUTE_MS
            redis.execute(
                RECORD_SCRIPT,
                listOf(ipAttemptsKey(ip), ipEmailsKey(ip), accountFailuresKey(email), globalAttemptsKey(minute), globalFailuresKey(minute)),
                now.toString(), UUID.randomUUID().toString(), sha256(email),
                ipWindowMs.toString(), distinctWindowMs.toString(), accountWindowMs.toString(), bucketTtlSeconds.toString(),
                flag(outcome.countsAsAccountFailure), flag(outcome.countsGlobally), flag(outcome.countsAsGlobalFailure),
            )
        }
    }

    private inline fun <T> guarded(block: () -> T): T =
        try {
            block()
        } catch (ex: RuntimeException) {
            log.warn("Contadores de riesgo no disponibles ({}): se aplica fail-safe", ex.javaClass.name)
            throw RiskCountersUnavailableException()
        }

    private fun ipAttemptsKey(ip: String) = "risk:ip:$ip:attempts"
    private fun ipEmailsKey(ip: String) = "risk:ip:$ip:emails"
    private fun accountFailuresKey(email: String) = "risk:account:${sha256(email)}:failures"
    private fun globalAttemptsKey(minute: Long) = "risk:global:$minute:attempts"
    private fun globalFailuresKey(minute: Long) = "risk:global:$minute:failures"

    private fun flag(value: Boolean) = if (value) "1" else "0"

    private fun sha256(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private companion object {
        const val MINUTE_MS = 60_000L

        // KEYS: 1 intentos IP, 2 emails IP, 3 fallos cuenta, 4..3+n buckets de intentos, 4+n..3+2n buckets de fallos.
        // ARGV: 1-3 inicio de cada ventana (ms), 4 n buckets.
        val READ_SCRIPT: RedisScript<List<Long>> = RedisScript.of(
            """
            local r = {}
            r[1] = redis.call('ZCOUNT', KEYS[1], ARGV[1], '+inf')
            r[2] = redis.call('ZCOUNT', KEYS[2], ARGV[2], '+inf')
            r[3] = redis.call('ZCOUNT', KEYS[3], ARGV[3], '+inf')
            local n = tonumber(ARGV[4])
            local attempts, failures = 0, 0
            for i = 1, n do
              attempts = attempts + tonumber(redis.call('GET', KEYS[3 + i]) or '0')
              failures = failures + tonumber(redis.call('GET', KEYS[3 + n + i]) or '0')
            end
            r[4] = attempts
            r[5] = failures
            return r
            """.trimIndent(),
            List::class.java,
        ) as RedisScript<List<Long>>

        // KEYS: 1 intentos IP, 2 emails IP, 3 fallos cuenta, 4 bucket intentos, 5 bucket fallos.
        // ARGV: 1 ahora, 2 id único, 3 hash email, 4-6 ventanas (ms), 7 TTL bucket (s),
        //       8 ¿fallo de cuenta?, 9 ¿cuenta global?, 10 ¿fallo global?
        val RECORD_SCRIPT: RedisScript<java.lang.Long> = RedisScript.of(
            """
            local now = tonumber(ARGV[1])
            local function slide(key, member, window)
              redis.call('ZADD', key, now, member)
              redis.call('ZREMRANGEBYSCORE', key, '-inf', '(' .. (now - window))
              redis.call('PEXPIRE', key, window)
            end
            slide(KEYS[1], ARGV[2], tonumber(ARGV[4]))
            slide(KEYS[2], ARGV[3], tonumber(ARGV[5]))
            if ARGV[8] == '1' then slide(KEYS[3], ARGV[2], tonumber(ARGV[6])) end
            if ARGV[9] == '1' then
              redis.call('INCR', KEYS[4])
              redis.call('EXPIRE', KEYS[4], ARGV[7])
            end
            if ARGV[10] == '1' then
              redis.call('INCR', KEYS[5])
              redis.call('EXPIRE', KEYS[5], ARGV[7])
            end
            return 1
            """.trimIndent(),
            java.lang.Long::class.java,
        )
    }
}
