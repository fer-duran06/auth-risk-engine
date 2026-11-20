package com.authrisk.application.auth

import com.authrisk.application.auth.AuthFixture.Companion.PASSWORD
import com.authrisk.domain.user.InvalidCredentialsException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LoginUseCaseTest {
    private val f = AuthFixture()

    @Test
    fun `login correcto emite tokens y guarda solo el hash del refresh`() {
        val tokens = f.registerAndLogin()

        val record = f.refreshTokens.tokens.values.single()
        assertEquals(f.generator.hash(tokens.refreshToken), record.tokenHash)
        assertEquals(f.clock.now.plus(f.refreshTtl), record.expiresAt)
        assertEquals(record.expiresAt, tokens.refreshTokenExpiresAt)
        assertNull(record.revokedAt)
    }

    @Test
    fun `email sin distinguir mayúsculas`() {
        f.register.execute("ana@example.com", PASSWORD)
        f.login.execute("ANA@Example.com", PASSWORD)
    }

    @Test
    fun `contraseña incorrecta y usuario inexistente dan la misma excepción`() {
        f.register.execute("ana@example.com", PASSWORD)

        val wrong = assertFailsWith<InvalidCredentialsException> { f.login.execute("ana@example.com", "incorrecta-123") }
        val missing = assertFailsWith<InvalidCredentialsException> { f.login.execute("nadie@example.com", PASSWORD) }
        val malformed = assertFailsWith<InvalidCredentialsException> { f.login.execute("no-es-email", PASSWORD) }

        assertEquals(wrong.message, missing.message)
        assertEquals(wrong.message, malformed.message)
        assertEquals(0, f.refreshTokens.tokens.size)
    }

    @Test
    fun `usuario inexistente igual ejecuta una verificación de hash`() {
        val before = f.hasher.matchCalls
        assertFailsWith<InvalidCredentialsException> { f.login.execute("nadie@example.com", PASSWORD) }
        assertEquals(before + 1, f.hasher.matchCalls)
    }

    @Test
    fun `cada login abre una familia nueva`() {
        val a = f.registerAndLogin()
        val b = f.login.execute("ana@example.com", PASSWORD)
        assert(f.recordOf(a.refreshToken).familyId != f.recordOf(b.refreshToken).familyId)
    }
}
