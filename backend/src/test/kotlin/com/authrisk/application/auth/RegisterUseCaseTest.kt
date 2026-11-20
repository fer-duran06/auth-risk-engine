package com.authrisk.application.auth

import com.authrisk.application.auth.AuthFixture.Companion.PASSWORD
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.InvalidEmailException
import com.authrisk.domain.user.Role
import com.authrisk.domain.user.WeakPasswordException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RegisterUseCaseTest {
    private val f = AuthFixture()

    @Test
    fun `registra con email normalizado, rol USER y contraseña hasheada`() {
        f.register.execute("  Ana@Example.COM ", PASSWORD)

        val user = f.users.findByEmail(Email.of("ana@example.com"))!!
        assertEquals("ana@example.com", user.email.value)
        assertEquals(Role.USER, user.role)
        assertNotEquals(PASSWORD, user.passwordHash)
        assertEquals(f.clock.now, user.createdAt)
    }

    @Test
    fun `email existente no falla, no sobrescribe y hashea igual`() {
        val hashesBefore = f.hasher.hashCalls
        f.register.execute("ana@example.com", PASSWORD)
        val original = f.users.findByEmail(Email.of("ana@example.com"))!!

        f.register.execute("ANA@example.com", "otra-contrasena-larga")

        assertEquals(1, f.users.users.size)
        assertEquals(original, f.users.findByEmail(Email.of("ana@example.com")))
        // Mismo trabajo en ambos casos: el hash se calcula aunque el email exista.
        assertEquals(hashesBefore + 2, f.hasher.hashCalls)
    }

    @Test
    fun `rechaza email inválido y contraseña fuera de política`() {
        assertFailsWith<InvalidEmailException> { f.register.execute("no-es-email", PASSWORD) }
        assertFailsWith<WeakPasswordException> { f.register.execute("ana@example.com", "corta") }
        assertFailsWith<WeakPasswordException> { f.register.execute("ana@example.com", "x".repeat(129)) }
        assertEquals(0, f.users.users.size)
    }
}
