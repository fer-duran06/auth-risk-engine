package com.authrisk.domain.user

/**
 * Política mínima de contraseñas. El máximo acota el costo de Argon2 ante entradas enormes.
 * La verificación contra filtraciones (HIBP) se agrega en una fase posterior.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 128

    fun validate(raw: String) {
        if (raw.length !in MIN_LENGTH..MAX_LENGTH) throw WeakPasswordException()
    }
}
