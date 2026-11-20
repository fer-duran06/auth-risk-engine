package com.authrisk.domain

import com.authrisk.domain.token.RefreshToken
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.InvalidEmailException
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainTest {

    @Test
    fun `email se normaliza y valida`() {
        assertEquals("ana@example.com", Email.of("  ANA@Example.Com ").value)
        assertNull(Email.parseOrNull("sin-arroba"))
        assertNull(Email.parseOrNull("a@b"))
        assertNull(Email.parseOrNull("a b@example.com"))
        assertNull(Email.parseOrNull("a".repeat(250) + "@x.com"))
        assertFailsWith<InvalidEmailException> { Email.of("") }
    }

    @Test
    fun `refresh token activo, expirado y rotado`() {
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val token = RefreshToken(UUID.randomUUID(), UUID.randomUUID(), "h", UUID.randomUUID(), now.plusSeconds(60), null, null, now)

        assertTrue(token.isActive(now))
        assertFalse(token.isActive(now.plusSeconds(60)))

        val next = UUID.randomUUID()
        val rotated = token.rotateTo(next, now)
        assertTrue(rotated.isRevoked)
        assertEquals(next, rotated.replacedBy)
        assertFalse(rotated.isActive(now))
    }
}
