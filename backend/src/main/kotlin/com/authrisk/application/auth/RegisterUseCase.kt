package com.authrisk.application.auth

import com.authrisk.application.port.TransactionRunner
import com.authrisk.domain.port.PasswordHasher
import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.PasswordPolicy
import com.authrisk.domain.user.Role
import com.authrisk.domain.user.User
import java.time.Clock
import java.util.UUID

/**
 * Registra un usuario con rol USER.
 * Si el email ya existe no falla ni lo indica: el hash se calcula igual para que el tiempo
 * de respuesta sea similar y el resultado observable es idéntico (sin enumeración).
 */
class RegisterUseCase(
    private val users: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(rawEmail: String, rawPassword: String) {
        val email = Email.of(rawEmail)
        PasswordPolicy.validate(rawPassword)
        val user = User(
            id = UUID.randomUUID(),
            email = email,
            passwordHash = passwordHasher.hash(rawPassword),
            role = Role.USER,
            createdAt = clock.instant(),
        )
        tx.inTransaction { users.insertIfAbsent(user) }
    }
}
