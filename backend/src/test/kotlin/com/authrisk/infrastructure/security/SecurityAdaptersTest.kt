package com.authrisk.infrastructure.security

import com.authrisk.domain.user.Email
import com.authrisk.domain.user.Role
import com.authrisk.domain.user.User
import org.springframework.security.oauth2.jwt.JwtValidationException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecurityAdaptersTest {

    @Test
    fun `argon2id con sal distinta por hash`() {
        val hasher = Argon2PasswordHasher()
        val a = hasher.hash("contrasena-larga-1")
        val b = hasher.hash("contrasena-larga-1")

        assertTrue(a.startsWith("\$argon2id\$"))
        assertNotEquals(a, b)
        assertTrue(hasher.matches("contrasena-larga-1", a))
        assertFalse(hasher.matches("otra-contrasena", a))
    }

    @Test
    fun `refresh token aleatorio de 256 bits y hash SHA-256 determinista`() {
        val gen = SecureRefreshTokenGenerator()
        val token = gen.generate()

        assertEquals(43, token.length) // 32 bytes en base64url sin relleno
        assertNotEquals(token, gen.generate())
        assertEquals(64, gen.hash(token).length)
        assertEquals(gen.hash(token), gen.hash(token))
    }

    @Test
    fun `jwt HS256 con sub, role y 15 minutos de vida`() {
        val props = AuthProperties(jwtSecret = "s".repeat(32))
        val config = JwtConfig(props)
        val key = config.jwtSigningKey()
        val now = Instant.now()
        val issuer = JwtTokenIssuer(config.jwtEncoder(key), props, Clock.fixed(now, ZoneOffset.UTC))
        val user = User(UUID.randomUUID(), Email.of("ana@example.com"), "hash", Role.ADMIN, now)

        val token = issuer.issueAccessToken(user)
        val jwt = config.jwtDecoder(key).decode(token.value)

        assertEquals(user.id.toString(), jwt.subject)
        assertEquals("ADMIN", jwt.getClaimAsString("role"))
        assertEquals("HS256", jwt.headers["alg"])
        assertEquals(now.plus(props.accessTokenTtl).epochSecond, jwt.expiresAt!!.epochSecond)
        assertFalse(jwt.claims.containsKey("email"))
    }

    @Test
    fun `jwt expirado o de otro emisor se rechaza`() {
        val props = AuthProperties(jwtSecret = "s".repeat(32))
        val config = JwtConfig(props)
        val key = config.jwtSigningKey()
        val user = User(UUID.randomUUID(), Email.of("ana@example.com"), "hash", Role.USER, Instant.now())
        val longAgo = Clock.fixed(Instant.now().minusSeconds(3600), ZoneOffset.UTC)

        val expired = JwtTokenIssuer(config.jwtEncoder(key), props, longAgo).issueAccessToken(user)
        assertFailsWith<JwtValidationException> { config.jwtDecoder(key).decode(expired.value) }

        val foreign = JwtTokenIssuer(config.jwtEncoder(key), props.copy(jwtIssuer = "otro"), Clock.systemUTC()).issueAccessToken(user)
        assertFailsWith<JwtValidationException> { config.jwtDecoder(key).decode(foreign.value) }
    }

    @Test
    fun `secreto JWT ausente o corto impide arrancar`() {
        assertFailsWith<IllegalArgumentException> { JwtConfig(AuthProperties(jwtSecret = "")).jwtSigningKey() }
        assertFailsWith<IllegalArgumentException> { JwtConfig(AuthProperties(jwtSecret = "corto")).jwtSigningKey() }
    }
}
