package com.authrisk.infrastructure.web.auth

import com.authrisk.infrastructure.security.AuthProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Cookie del refresh token: HttpOnly (inaccesible a JS), SameSite=Strict (no viaja en peticiones
 * cross-site, mitiga CSRF) y Path limitado a /api/auth. Secure configurable por perfil.
 */
@Component
class RefreshCookieFactory(private val props: AuthProperties, private val clock: Clock) {

    fun create(rawToken: String, expiresAt: Instant): ResponseCookie =
        base(rawToken).maxAge(Duration.between(clock.instant(), expiresAt).coerceAtLeast(Duration.ZERO)).build()

    /** Cookie vacía con Max-Age=0 para que el navegador la elimine. */
    fun clear(): ResponseCookie = base("").maxAge(Duration.ZERO).build()

    private fun base(value: String) = ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(props.cookieSecure)
        .sameSite("Strict")
        .path(PATH)

    companion object {
        const val NAME = "refresh_token"
        const val PATH = "/api/auth"
    }
}
