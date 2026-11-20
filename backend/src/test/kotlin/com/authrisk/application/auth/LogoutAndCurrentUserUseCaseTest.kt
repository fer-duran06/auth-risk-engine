package com.authrisk.application.auth

import com.authrisk.domain.token.InvalidRefreshTokenException
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.UserNotFoundException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LogoutAndCurrentUserUseCaseTest {
    private val f = AuthFixture()

    @Test
    fun `logout revoca toda la familia`() {
        val first = f.registerAndLogin()
        val second = f.refresh.execute(first.refreshToken)

        f.logout.execute(second.refreshToken)

        assertTrue(f.refreshTokens.family(f.recordOf(second.refreshToken).familyId).all { it.isRevoked })
        assertFailsWith<InvalidRefreshTokenException> { f.refresh.execute(second.refreshToken) }
    }

    @Test
    fun `logout es idempotente y tolera tokens ausentes o desconocidos`() {
        val tokens = f.registerAndLogin()
        f.logout.execute(tokens.refreshToken)
        f.logout.execute(tokens.refreshToken)
        f.logout.execute(null)
        f.logout.execute("inventado")
    }

    @Test
    fun `get current user devuelve el usuario o falla si no existe`() {
        f.register.execute("ana@example.com", AuthFixture.PASSWORD)
        val user = f.users.findByEmail(Email.of("ana@example.com"))!!

        assertEquals(user, f.currentUser.execute(user.id))
        assertFailsWith<UserNotFoundException> { f.currentUser.execute(UUID.randomUUID()) }
    }
}
