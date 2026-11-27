package com.authrisk.infrastructure.redis

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component

/** Resultado de una comprobación de límite: si se permite y, si no, en cuántos segundos reintentar. */
data class RateLimitResult(val allowed: Boolean, val retryAfterSeconds: Long)

/**
 * Limitador de ventana fija en Redis: un `INCR` + `EXPIRE` atómico por clave y ventana.
 *
 * Fail-open a propósito: si Redis no responde, se permite la solicitud. El motor de riesgo ya
 * aplica su propio fail-safe (CHALLENGE) específicamente para login; negar aquí todo el tráfico
 * de autenticación por una caída breve de Redis sería más dañino que el riesgo que mitiga este
 * filtro (es una capa de perímetro contra volumen, no la defensa principal contra robo de cuentas).
 */
@Component
class RedisRateLimiter(private val redis: StringRedisTemplate, private val props: RateLimitProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val windowSeconds = props.window.seconds

    fun check(key: String): RateLimitResult {
        if (!props.enabled) return RateLimitResult(allowed = true, retryAfterSeconds = 0)
        return try {
            val bucket = System.currentTimeMillis() / (windowSeconds * 1000)
            val redisKey = "ratelimit:$key:$bucket"
            val count = redis.execute(SCRIPT, listOf(redisKey), windowSeconds.toString())
            if (count != null && count > props.capacity) {
                val elapsed = (System.currentTimeMillis() / 1000) % windowSeconds
                RateLimitResult(allowed = false, retryAfterSeconds = windowSeconds - elapsed)
            } else {
                RateLimitResult(allowed = true, retryAfterSeconds = 0)
            }
        } catch (ex: RuntimeException) {
            log.warn("Rate limiter no disponible ({}): se permite la solicitud (fail-open)", ex.javaClass.name)
            RateLimitResult(allowed = true, retryAfterSeconds = 0)
        }
    }

    private companion object {
        // KEYS[1]: contador de la ventana actual. ARGV[1]: TTL en segundos.
        // java.lang.Long (no Long::class.java, que en Kotlin es el primitivo long.class):
        // el deserializador de Spring Data Redis espera el tipo envuelto (ver RedisRiskCounters).
        val SCRIPT: RedisScript<java.lang.Long> = RedisScript.of(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return count
            """.trimIndent(),
            java.lang.Long::class.java,
        )
    }
}
