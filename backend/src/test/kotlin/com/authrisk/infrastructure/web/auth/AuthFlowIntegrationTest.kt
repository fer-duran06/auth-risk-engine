package com.authrisk.infrastructure.web.auth

import com.authrisk.IntegrationTestBase
import com.authrisk.infrastructure.persistence.RefreshTokenJpaRepository
import com.authrisk.infrastructure.persistence.UserJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Flujo completo de autenticación contra PostgreSQL real. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var users: UserJpaRepository
    @Autowired lateinit var refreshTokens: RefreshTokenJpaRepository

    private val password = "contrasena-larga-1"
    private fun newEmail() = "user-${UUID.randomUUID()}@example.com"

    private fun register(email: String, pwd: String = password) =
        mvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(mapOf("email" to email, "password" to pwd))
        }

    private fun login(email: String, pwd: String = password) =
        mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(mapOf("email" to email, "password" to pwd))
        }

    private fun refresh(token: String?) = mvc.post("/api/auth/refresh") { token?.let { cookie(Cookie(RefreshCookieFactory.NAME, it)) } }

    private fun logout(token: String) = mvc.post("/api/auth/logout") { cookie(Cookie(RefreshCookieFactory.NAME, token)) }

    private fun MvcResult.refreshCookie(): String = response.getCookie(RefreshCookieFactory.NAME)!!.value
    private fun MvcResult.accessToken(): String = json.readTree(response.contentAsString)["accessToken"].asText()

    private fun loginOk(email: String): MvcResult = login(email).andExpect { status { isOk() } }.andReturn()

    @Test
    fun `register responde 202 genérico y guarda el email en minúsculas con hash Argon2id`() {
        val email = newEmail()
        val first = register(email.uppercase()).andExpect { status { isAccepted() } }.andReturn()

        val stored = users.findByEmail(email)!!
        assertTrue(stored.passwordHash.startsWith("\$argon2id\$"))
        assertFalse(first.response.contentAsString.contains(password))

        // Email repetido: misma respuesta, sin revelar que existe y sin sobrescribir.
        val second = register(email, "otra-contrasena-larga").andExpect { status { isAccepted() } }.andReturn()
        assertEquals(first.response.contentAsString, second.response.contentAsString)
        assertEquals(stored.passwordHash, users.findByEmail(email)!!.passwordHash)
    }

    @Test
    fun `register valida email y contraseña`() {
        register("no-es-email").andExpect { status { isBadRequest() } }
        register(newEmail(), "corta").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `login emite access token y cookie HttpOnly SameSite Strict, y me devuelve el usuario`() {
        val email = newEmail()
        register(email)

        val result = loginOk(email)
        val setCookie = result.response.getHeader(HttpHeaders.SET_COOKIE)!!
        assertTrue(setCookie.contains("HttpOnly"))
        assertTrue(setCookie.contains("SameSite=Strict"))
        assertTrue(setCookie.contains("Path=/api/auth"))
        val body = json.readTree(result.response.contentAsString)
        assertEquals("Bearer", body["tokenType"].asText())
        assertTrue(body["expiresIn"].asInt() in 890..900) // 15 min (truncado a segundos)
        assertFalse(body.has("refreshToken"))

        // En BD solo está el hash, nunca el token en claro.
        val raw = result.refreshCookie()
        assertTrue(refreshTokens.findAll().none { it.tokenHash == raw })

        mvc.get("/api/auth/me") { header(HttpHeaders.AUTHORIZATION, "Bearer ${result.accessToken()}") }
            .andExpect {
                status { isOk() }
                jsonPath("$.email") { value(email) }
                jsonPath("$.role") { value("USER") }
                jsonPath("$.passwordHash") { doesNotExist() }
            }
    }

    @Test
    fun `login falla igual con contraseña incorrecta o usuario inexistente`() {
        val email = newEmail()
        register(email)

        val wrong = login(email, "incorrecta-1234").andExpect { status { isUnauthorized() } }.andReturn()
        val missing = login(newEmail()).andExpect { status { isUnauthorized() } }.andReturn()

        assertEquals(wrong.response.contentAsString, missing.response.contentAsString)
        assertNull(wrong.response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `me sin token o con token inválido responde 401`() {
        mvc.get("/api/auth/me").andExpect { status { isUnauthorized() } }
        mvc.get("/api/auth/me") { header(HttpHeaders.AUTHORIZATION, "Bearer no.es.jwt") }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `refresh rota el token y el nuevo sirve`() {
        val email = newEmail()
        register(email)
        val first = loginOk(email).refreshCookie()

        val rotated = refresh(first).andExpect { status { isOk() } }.andReturn()
        val second = rotated.refreshCookie()
        assertNotEquals(first, second)
        assertNotNull(rotated.accessToken())

        refresh(second).andExpect { status { isOk() } }
    }

    @Test
    fun `reutilizar un refresh rotado revoca toda la familia en BD`() {
        val email = newEmail()
        register(email)
        val first = loginOk(email).refreshCookie()
        val second = refresh(first).andExpect { status { isOk() } }.andReturn().refreshCookie()

        refresh(first).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("Sesión inválida o expirada") }
            cookie { maxAge(RefreshCookieFactory.NAME, 0) }
        }

        val userId = users.findByEmail(email)!!.id
        val family = refreshTokens.findAll().filter { it.userId == userId }
        assertEquals(2, family.size)
        assertTrue(family.all { it.revokedAt != null })
        // El token legítimo más reciente también quedó revocado.
        refresh(second).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `refresh sin cookie responde 401`() {
        refresh(null).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `logout revoca la familia y borra la cookie`() {
        val email = newEmail()
        register(email)
        val first = loginOk(email).refreshCookie()
        val second = refresh(first).andReturn().refreshCookie()

        logout(second).andExpect {
            status { isNoContent() }
            cookie { maxAge(RefreshCookieFactory.NAME, 0) }
        }

        refresh(second).andExpect { status { isUnauthorized() } }
        val userId = users.findByEmail(email)!!.id
        assertTrue(refreshTokens.findAll().filter { it.userId == userId }.all { it.revokedAt != null })
        // Idempotente.
        logout(second).andExpect { status { isNoContent() } }
    }

    private fun assertNull(value: Any?) = kotlin.test.assertNull(value)
}
