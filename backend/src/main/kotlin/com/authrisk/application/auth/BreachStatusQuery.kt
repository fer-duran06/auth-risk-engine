package com.authrisk.application.auth

import com.authrisk.domain.port.BreachCheckPort
import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.user.UserNotFoundException
import java.util.UUID

/**
 * Brechas conocidas (XposedOrNot) para el correo del usuario autenticado.
 * Deliberadamente no se persiste: se consulta en vivo cada vez que abre su página de seguridad,
 * así siempre refleja el estado actual de las bases de datos de terceros, no una foto del registro.
 */
class BreachStatusQuery(private val users: UserRepository, private val breachCheck: BreachCheckPort) {
    fun execute(userId: UUID): BreachStatusResult {
        val user = users.findById(userId) ?: throw UserNotFoundException()
        return BreachStatusResult(breachCount = breachCheck.breachesForEmail(user.email.value))
    }
}

/** [breachCount] es null cuando el servicio de terceros no respondió (no significa "sin brechas"). */
data class BreachStatusResult(val breachCount: Int?)
