package com.authrisk.infrastructure.redis

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Límite de solicitudes por IP a los endpoints sensibles de autenticación (prefijo `app.rate-limit`).
 * Es una capa aparte del motor de riesgo: un filtro HTTP, ciego al resultado del login, pensado
 * contra el volumen puro (fuerza bruta/DoS) en endpoints que hoy no pasan por el motor de riesgo
 * (registro, refresh, verificación de 2FA). Ventana fija (ver [RedisRateLimiter]): más simple que
 * un token bucket, a costa de permitir hasta 2x [capacity] en la frontera entre dos ventanas.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
data class RateLimitProperties(
    val enabled: Boolean = true,
    val capacity: Int = 60,
    val window: Duration = Duration.ofMinutes(1),
) {
    init {
        require(capacity > 0) { "La capacidad debe ser positiva" }
        require(!window.isNegative && !window.isZero) { "La ventana debe ser positiva" }
    }
}
