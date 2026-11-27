package com.authrisk.domain.user

/**
 * Política mínima de contraseñas. El máximo acota el costo de Argon2 ante entradas enormes.
 * La verificación contra contraseñas filtradas (Pwned Passwords) vive en [RegisterUseCase]
 * (es una llamada de red, no una regla pura de dominio) y usa [com.authrisk.domain.port.BreachCheckPort].
 */
object PasswordPolicy {
    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 128

    fun validate(raw: String) {
        if (raw.length !in MIN_LENGTH..MAX_LENGTH) throw WeakPasswordException()
    }
}
