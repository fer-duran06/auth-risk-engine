package com.authrisk.infrastructure.security

import com.authrisk.domain.port.PasswordHasher
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Argon2id con sal aleatoria de 16 bytes por contraseña (parámetros recomendados por Spring Security 5.8+:
 * 16 MiB de memoria, 2 iteraciones, paralelismo 1). El hash codificado incluye parámetros y sal.
 */
@Component
class Argon2PasswordHasher : PasswordHasher {
    private val encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    override fun hash(rawPassword: String): String = encoder.encode(rawPassword)

    override fun matches(rawPassword: String, passwordHash: String): Boolean = encoder.matches(rawPassword, passwordHash)
}
