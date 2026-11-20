package com.authrisk.infrastructure.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuración de autenticación (prefijo `app.auth`).
 * [jwtSecret] llega solo por la variable de entorno JWT_SECRET; nunca se escribe en código.
 */
@ConfigurationProperties(prefix = "app.auth")
data class AuthProperties(
    val jwtSecret: String = "",
    val jwtIssuer: String = "authshield",
    val accessTokenTtl: Duration = Duration.ofMinutes(15),
    val refreshTokenTtl: Duration = Duration.ofDays(7),
    /** Atributo Secure de la cookie de refresh. true por defecto; el perfil dev lo desactiva (http://localhost). */
    val cookieSecure: Boolean = true,
) {
    // Evita exponer el secreto si la configuración se imprime.
    override fun toString(): String =
        "AuthProperties(jwtIssuer=$jwtIssuer, accessTokenTtl=$accessTokenTtl, refreshTokenTtl=$refreshTokenTtl, cookieSecure=$cookieSecure)"
}
