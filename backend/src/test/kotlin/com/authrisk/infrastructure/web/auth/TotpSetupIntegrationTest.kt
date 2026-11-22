package com.authrisk.infrastructure.web.auth

import com.authrisk.IntegrationTestBase
import com.authrisk.domain.totp.Base32
import com.authrisk.domain.totp.Totp
import com.authrisk.infrastructure.persistence.UserJpaRepository
import com.authrisk.infrastructure.persistence.UserTotpJpaRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Setup, enable y status del 2FA con PostgreSQL real y la clave AES-GCM de la configuración. */
@SpringBootTest
@AutoConfigureMockMvc
class TotpSetupIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var users: UserJpaRepository
    @Autowired lateinit var totpRows: UserTotpJpaRepository

    private val totp = Totp()
    private val password = "contrasena-larga-1"

    private fun registerAndLogin(): Pair<String, UUID> {
        val email = "user-${UUID.randomUUID()}@example.com"
        val body = json.writeValueAsString(mapOf("email" to email, "password" to password))
        mvc.post("/api/auth/register") { contentType = MediaType.APPLICATION_JSON; content = body }
        val res = mvc.post("/api/auth/login") { contentType = MediaType.APPLICATION_JSON; content = body }
            .andExpect { status { isOk() } }.andReturn()
        return json.readTree(res.response.contentAsString)["accessToken"].asText() to users.findByEmail(email)!!.id
    }

    private fun setup(token: String) = mvc.post("/api/auth/2fa/setup") { header("Authorization", "Bearer $token") }

    private fun enable(token: String, code: String) = mvc.post("/api/auth/2fa/enable") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = json.writeValueAsString(mapOf("code" to code))
    }

    private fun currentCode(secret: String) = totp.generate(Base32.decode(secret), totp.stepAt(Instant.now()))

    @Test
    fun `setup, enable y status de punta a punta`() {
        val (token, userId) = registerAndLogin()
        mvc.get("/api/auth/2fa/status") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() }; jsonPath("$.enabled") { value(false) } }

        val res = setup(token).andExpect {
            status { isOk() }
            jsonPath("$.otpauthUri") { value(org.hamcrest.Matchers.startsWith("otpauth://totp/AuthShield:")) }
        }.andReturn()
        val secret = json.readTree(res.response.contentAsString)["secret"].asText()

        // En BD solo está cifrado y ligado al usuario.
        val row = totpRows.findById(userId).orElseThrow()
        assertFalse(row.enabled)
        assertFalse(row.secretEncrypted.contains(secret))

        enable(token, "000000").andExpect { status { isBadRequest() }; jsonPath("$.error") { value("Código inválido") } }
        enable(token, currentCode(secret)).andExpect { status { isNoContent() } }
        // El mismo código no se acepta dos veces (ni siquiera aquí).
        enable(token, currentCode(secret)).andExpect { status { isConflict() } }

        mvc.get("/api/auth/2fa/status") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            jsonPath("$.enabled") { value(true) }
            jsonPath("$.confirmedAt") { exists() }
            jsonPath("$.secret") { doesNotExist() }
        }
        val enabled = totpRows.findById(userId).orElseThrow()
        assertTrue(enabled.enabled)
        assertNotNull(enabled.lastUsedStep)

        setup(token).andExpect { status { isConflict() } }
        assertEquals(enabled.secretEncrypted, totpRows.findById(userId).orElseThrow().secretEncrypted)
    }

    @Test
    fun `los endpoints de gestión exigen Bearer`() {
        mvc.post("/api/auth/2fa/setup").andExpect { status { isUnauthorized() } }
        mvc.post("/api/auth/2fa/enable") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"code":"123456"}"""
        }.andExpect { status { isUnauthorized() } }
        mvc.get("/api/auth/2fa/status").andExpect { status { isUnauthorized() } }
    }
}
