package com.authrisk.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuración del 2FA (prefijo `app.two-factor`).
 * [encryptionKey] llega solo por la variable de entorno TOTP_ENCRYPTION_KEY.
 */
@ConfigurationProperties(prefix = "app.two-factor")
data class TwoFactorProperties(
    val encryptionKey: String = "",
    /** Emisor que muestra la app autenticadora. */
    val issuer: String = "AuthShield",
    /** Vigencia del challengeId emitido en zona CHALLENGE. */
    val challengeTtl: Duration = Duration.ofMinutes(5),
    /** Intentos de verificación por challengeId; al agotarlos el desafío se invalida. */
    val maxAttempts: Int = 5,
    /** Cada cuánto se purgan los desafíos vencidos o cerrados. */
    val cleanupInterval: Duration = Duration.ofMinutes(5),
) {
    init {
        require(!challengeTtl.isNegative && !challengeTtl.isZero) { "challenge-ttl debe ser positivo" }
        require(maxAttempts in 1..20) { "max-attempts debe estar entre 1 y 20" }
        require(!cleanupInterval.isNegative && !cleanupInterval.isZero) { "cleanup-interval debe ser positivo" }
    }

    override fun toString(): String =
        "TwoFactorProperties(issuer=$issuer, challengeTtl=$challengeTtl, maxAttempts=$maxAttempts, cleanupInterval=$cleanupInterval)"
}
