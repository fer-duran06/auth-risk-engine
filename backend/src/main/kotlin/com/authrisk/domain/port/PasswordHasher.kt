package com.authrisk.domain.port

/** Hash de contraseñas con sal (Argon2id en infraestructura). */
interface PasswordHasher {
    fun hash(rawPassword: String): String
    fun matches(rawPassword: String, passwordHash: String): Boolean
}
