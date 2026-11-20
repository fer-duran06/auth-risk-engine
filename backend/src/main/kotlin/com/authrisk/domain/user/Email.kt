package com.authrisk.domain.user

/**
 * Email normalizado (sin espacios y en minúsculas): es la identidad de inicio de sesión.
 * La validación es deliberadamente simple; no pretende cubrir todo el RFC 5322.
 */
@JvmInline
value class Email private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH = 254
        private val FORMAT = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

        /** Normaliza y valida; lanza [InvalidEmailException] si el formato no es válido. */
        fun of(raw: String): Email = parseOrNull(raw) ?: throw InvalidEmailException()

        /** Variante sin excepción, útil en login para no distinguir casos inválidos. */
        fun parseOrNull(raw: String): Email? {
            val normalized = raw.trim().lowercase()
            if (normalized.length > MAX_LENGTH || !FORMAT.matches(normalized)) return null
            return Email(normalized)
        }
    }
}
