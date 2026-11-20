package com.authrisk.domain.user

import java.time.Instant
import java.util.UUID

/** Usuario registrado. [passwordHash] es un hash Argon2id codificado; nunca la contraseña. */
data class User(
    val id: UUID,
    val email: Email,
    val passwordHash: String,
    val role: Role,
    val createdAt: Instant,
) {
    // Evita que el hash termine en logs por accidente.
    override fun toString(): String = "User(id=$id, email=$email, role=$role, createdAt=$createdAt)"
}
