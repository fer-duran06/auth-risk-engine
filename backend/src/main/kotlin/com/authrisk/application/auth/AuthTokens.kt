package com.authrisk.application.auth

import com.authrisk.domain.token.AccessToken
import java.time.Instant

/**
 * Resultado de login/refresh. [refreshToken] va en claro solo para escribirse en la cookie;
 * nunca se persiste ni se registra.
 */
data class AuthTokens(
    val accessToken: AccessToken,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
) {
    override fun toString(): String = "AuthTokens(accessExpiresAt=${accessToken.expiresAt}, refreshExpiresAt=$refreshTokenExpiresAt)"
}
