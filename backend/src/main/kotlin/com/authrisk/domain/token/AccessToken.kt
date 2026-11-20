package com.authrisk.domain.token

import java.time.Instant

/** JWT de acceso firmado y su expiración. */
data class AccessToken(val value: String, val expiresAt: Instant) {
    override fun toString(): String = "AccessToken(expiresAt=$expiresAt)"
}
