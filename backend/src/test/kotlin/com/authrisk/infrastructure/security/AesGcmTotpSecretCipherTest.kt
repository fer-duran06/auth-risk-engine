package com.authrisk.infrastructure.security

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AesGcmTotpSecretCipherTest {
    private fun randomKey(bytes: Int = 32) = Base64.getEncoder().encodeToString(ByteArray(bytes).also(SecureRandom()::nextBytes))

    private val cipher = AesGcmTotpSecretCipher(randomKey())
    private val user = UUID.randomUUID()
    private val secret = "12345678901234567890".toByteArray()

    @Test
    fun `cifra y descifra, el texto cifrado no contiene el secreto y cambia en cada cifrado`() {
        val a = cipher.encrypt(user, secret)
        val b = cipher.encrypt(user, secret)
        assertContentEquals(secret, cipher.decrypt(user, a))
        assertNotEquals(a, b)
        assertFalse(String(Base64.getDecoder().decode(a), Charsets.ISO_8859_1).contains("1234567890"))
    }

    @Test
    fun `no descifra con otro usuario, otra clave ni si se alteró`() {
        val encrypted = cipher.encrypt(user, secret)
        assertFailsWith<AEADBadTagException> { cipher.decrypt(UUID.randomUUID(), encrypted) }
        assertFailsWith<AEADBadTagException> { AesGcmTotpSecretCipher(randomKey()).decrypt(user, encrypted) }
        val tampered = Base64.getDecoder().decode(encrypted).also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
        assertFailsWith<AEADBadTagException> { cipher.decrypt(user, Base64.getEncoder().encodeToString(tampered)) }
    }

    @Test
    fun `la clave debe ser base64 de exactamente 32 bytes y el error no la revela`() {
        listOf("", "   ", "no-es-base64-!!", randomKey(16), randomKey(31), randomKey(33)).forEach { key ->
            val ex = assertFailsWith<IllegalArgumentException> { AesGcmTotpSecretCipher(key) }
            assertTrue(ex.message!!.contains("TOTP_ENCRYPTION_KEY"))
            if (key.isNotBlank()) assertFalse(ex.message!!.contains(key))
        }
    }
}
