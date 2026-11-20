package com.authrisk.domain.port

import com.authrisk.domain.user.Email
import com.authrisk.domain.user.User
import java.util.UUID

/** Persistencia de usuarios. */
interface UserRepository {
    /** Inserta el usuario si el email no existe. Devuelve false si ya existía (sin lanzar error). */
    fun insertIfAbsent(user: User): Boolean
    fun findByEmail(email: Email): User?
    fun findById(id: UUID): User?
}
