package com.authrisk.application.auth

import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.user.User
import com.authrisk.domain.user.UserNotFoundException
import java.util.UUID

/** Devuelve el usuario autenticado (identificado por el `sub` del JWT). */
class GetCurrentUserUseCase(private val users: UserRepository) {
    fun execute(userId: UUID): User = users.findById(userId) ?: throw UserNotFoundException()
}
