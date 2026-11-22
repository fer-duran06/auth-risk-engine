package com.authrisk.infrastructure.security

import com.authrisk.domain.port.TotpSecretCipher
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM con nonce aleatorio de 12 bytes por cifrado. Se guarda base64(nonce ‖ texto cifrado ‖ tag).
 * El id del usuario va como datos asociados (AAD): un secreto copiado a otra fila no descifra.
 * La clave llega solo por TOTP_ENCRYPTION_KEY (base64 de 32 bytes); si falta o es inválida, la app no arranca.
 */
class AesGcmTotpSecretCipher(base64Key: String) : TotpSecretCipher {
    private val key: SecretKeySpec
    private val random = SecureRandom()

    init {
        val bytes = try {
            Base64.getDecoder().decode(base64Key.trim())
        } catch (_: IllegalArgumentException) {
            null
        }
        // El mensaje nunca incluye el valor recibido.
        require(bytes != null && bytes.size == KEY_BYTES) {
            "TOTP_ENCRYPTION_KEY no está definida o no es base64 de $KEY_BYTES bytes"
        }
        key = SecretKeySpec(bytes, "AES")
    }

    override fun encrypt(userId: UUID, secret: ByteArray): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(aad(userId))
        }
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(secret))
    }

    /** Lanza [javax.crypto.AEADBadTagException] si el dato fue alterado, es de otro usuario o la clave cambió. */
    override fun decrypt(userId: UUID, encrypted: String): ByteArray {
        val data = Base64.getDecoder().decode(encrypted)
        require(data.size > NONCE_BYTES) { "Secreto cifrado inválido" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, 0, NONCE_BYTES))
            updateAAD(aad(userId))
        }
        return cipher.doFinal(data, NONCE_BYTES, data.size - NONCE_BYTES)
    }

    private fun aad(userId: UUID): ByteArray =
        ByteBuffer.allocate(16).putLong(userId.mostSignificantBits).putLong(userId.leastSignificantBits).array()

    override fun toString(): String = "AesGcmTotpSecretCipher(***)"

    companion object {
        const val KEY_BYTES = 32
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
