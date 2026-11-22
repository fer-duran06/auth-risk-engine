package com.authrisk.infrastructure.web.auth

import com.authrisk.application.auth.AuthTokens
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration

/** Respuesta común al abrir o rotar una sesión: access token en el cuerpo y refresh en la cookie. */
@Component
class SessionResponses(private val cookies: RefreshCookieFactory, private val clock: Clock) {

    fun tokens(tokens: AuthTokens): ResponseEntity<TokenResponse> {
        val expiresIn = Duration.between(clock.instant(), tokens.accessToken.expiresAt).seconds.coerceAtLeast(0)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.create(tokens.refreshToken, tokens.refreshTokenExpiresAt).toString())
            .body(TokenResponse(accessToken = tokens.accessToken.value, expiresIn = expiresIn))
    }
}
