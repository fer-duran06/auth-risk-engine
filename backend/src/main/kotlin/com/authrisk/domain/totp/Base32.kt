package com.authrisk.domain.totp

/** Base32 (RFC 4648) sin relleno, el formato que esperan las apps autenticadoras. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(data: ByteArray): String {
        val out = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(ALPHABET[(buffer shr (bits - 5)) and 0x1f])
                bits -= 5
            }
        }
        if (bits > 0) out.append(ALPHABET[(buffer shl (5 - bits)) and 0x1f])
        return out.toString()
    }

    /** Acepta minúsculas, espacios y relleno '='. Lanza IllegalArgumentException si hay otros caracteres. */
    fun decode(text: String): ByteArray {
        val clean = text.uppercase().filterNot { it == ' ' || it == '=' }
        val out = java.io.ByteArrayOutputStream(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            require(value >= 0) { "Carácter Base32 inválido" }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
