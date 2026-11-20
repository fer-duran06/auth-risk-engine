package com.authrisk.application.auth

import com.authrisk.domain.token.InvalidRefreshTokenException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshUseCaseTest {
    private val f = AuthFixture()

    @Test
    fun `rota el token dentro de la misma familia`() {
        val first = f.registerAndLogin()
        f.clock.advance(Duration.ofMinutes(5))

        val second = f.refresh.execute(first.refreshToken)

        assertNotEquals(first.refreshToken, second.refreshToken)
        val old = f.recordOf(first.refreshToken)
        val new = f.recordOf(second.refreshToken)
        assertEquals(old.familyId, new.familyId)
        assertEquals(f.clock.now, old.revokedAt)
        assertEquals(new.id, old.replacedBy)
        assertNull(new.revokedAt)
    }

    @Test
    fun `reutilizar un token rotado revoca toda la familia`() {
        val first = f.registerAndLogin()
        val second = f.refresh.execute(first.refreshToken)

        val ex = assertFailsWith<InvalidRefreshTokenException> { f.refresh.execute(first.refreshToken) }

        assertTrue(ex.reuseDetected)
        val family = f.refreshTokens.family(f.recordOf(first.refreshToken).familyId)
        assertTrue(family.all { it.isRevoked })
        // El token legítimo más reciente también queda inutilizable.
        assertFailsWith<InvalidRefreshTokenException> { f.refresh.execute(second.refreshToken) }
    }

    @Test
    fun `la reutilización no afecta a otras sesiones del mismo usuario`() {
        val sessionA = f.registerAndLogin()
        val sessionB = f.login.execute("ana@example.com", AuthFixture.PASSWORD, IP)
        f.refresh.execute(sessionA.refreshToken)
        assertFailsWith<InvalidRefreshTokenException> { f.refresh.execute(sessionA.refreshToken) }

        f.refresh.execute(sessionB.refreshToken)
    }

    @Test
    fun `token expirado es inválido sin marcar reutilización`() {
        val tokens = f.registerAndLogin()
        f.clock.advance(f.refreshTtl)

        val ex = assertFailsWith<InvalidRefreshTokenException> { f.refresh.execute(tokens.refreshToken) }
        assertFalse(ex.reuseDetected)
    }

    @Test
    fun `token ausente o desconocido es inválido`() {
        assertFailsWith<InvalidRefreshTokenException> { f.refresh.execute(null) }
        assertFailsWith<InvalidRefreshTokenException> { f.refresh.execute(" ") }
        val ex = assertFailsWith<InvalidRefreshTokenException> { f.refresh.execute("inventado") }
        assertFalse(ex.reuseDetected)
    }
}
