package com.authrisk.infrastructure.security

import com.authrisk.domain.port.RefreshTokenGenerator
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

/**
 * Refresh tokens de 256 bits aleatorios (base64url). Se almacenan como SHA-256: al tener alta entropía
 * no necesitan un hash lento con sal, y el hash determinista permite buscarlos por índice.
 */
@Component
class SecureRefreshTokenGenerator : RefreshTokenGenerator {
    private val random = SecureRandom()

    override fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }

    companion object {
        const val TOKEN_BYTES = 32
    }
}
