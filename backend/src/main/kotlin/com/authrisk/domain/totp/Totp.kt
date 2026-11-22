package com.authrisk.domain.totp

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP según RFC 6238 (sobre HOTP, RFC 4226) con HMAC-SHA1.
 * [window] es la tolerancia en pasos hacia atrás y hacia adelante (desfase de reloj).
 */
class Totp(
    val digits: Int = 6,
    val periodSeconds: Long = 30,
    val window: Int = 1,
) {
    init {
        require(digits in 6..8) { "digits debe estar entre 6 y 8" }
        require(periodSeconds > 0) { "El periodo debe ser positivo" }
        require(window in 0..2) { "La ventana debe estar entre 0 y 2 pasos" }
    }

    private val modulus = POWERS_OF_TEN[digits]

    /** Paso de tiempo T = floor(unix / periodo). */
    fun stepAt(instant: Instant): Long = Math.floorDiv(instant.epochSecond, periodSeconds)

    /** Código HOTP del paso [step] con relleno de ceros a la izquierda. */
    fun generate(secret: ByteArray, step: Long): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(secret, ALGORITHM)) }
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(step).array())
        // Truncamiento dinámico (RFC 4226, sección 5.3).
        val offset = hash.last().toInt() and 0x0f
        val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
            ((hash[offset + 1].toInt() and 0xff) shl 16) or
            ((hash[offset + 2].toInt() and 0xff) shl 8) or
            (hash[offset + 3].toInt() and 0xff)
        return (binary % modulus).toString().padStart(digits, '0')
    }

    /**
     * Devuelve el paso cuyo código coincide con [code] dentro de la ventana, o null.
     * Se recorren todos los pasos y se compara en tiempo constante. El llamador debe rechazar
     * un paso igual o anterior al último usado (anti-reutilización).
     */
    fun verify(secret: ByteArray, code: String, now: Instant): Long? {
        if (code.length != digits || !code.all { it in '0'..'9' }) return null
        val current = stepAt(now)
        var matched: Long? = null
        for (step in current - window..current + window) {
            if (MessageDigest.isEqual(generate(secret, step).toByteArray(), code.toByteArray())) matched = step
        }
        return matched
    }

    companion object {
        private const val ALGORITHM = "HmacSHA1"

        /** 160 bits, el tamaño de salida de SHA-1 que recomienda el RFC 4226. */
        const val SECRET_BYTES = 20

        private val POWERS_OF_TEN = IntArray(9).also { p -> p[0] = 1; for (i in 1..8) p[i] = p[i - 1] * 10 }
        private val random = SecureRandom()

        fun newSecret(): ByteArray = ByteArray(SECRET_BYTES).also(random::nextBytes)
    }
}
