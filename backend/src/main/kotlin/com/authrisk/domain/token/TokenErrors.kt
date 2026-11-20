package com.authrisk.domain.token

/**
 * Refresh token ausente, desconocido, expirado o reutilizado.
 * [reuseDetected] indica que se reutilizó un token ya rotado y se revocó su familia.
 */
class InvalidRefreshTokenException(val reuseDetected: Boolean = false) : RuntimeException("Sesión inválida o expirada")
