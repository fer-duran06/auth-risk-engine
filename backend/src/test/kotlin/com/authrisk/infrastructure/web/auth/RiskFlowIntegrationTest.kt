package com.authrisk.infrastructure.web.auth

import com.authrisk.IntegrationTestBase
import com.authrisk.infrastructure.persistence.LoginAttemptJpaRepository
import com.authrisk.infrastructure.web.ClientIpResolver
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Motor de riesgo de punta a punta con PostgreSQL y Redis reales. */
@SpringBootTest
@AutoConfigureMockMvc
class RiskFlowIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var attempts: LoginAttemptJpaRepository

    private val password = "contrasena-larga-1"
    private fun newEmail() = "user-${UUID.randomUUID()}@example.com"
    // IP única por prueba: login_attempts no se limpia entre pruebas.
    private fun newIp() = "10.${Random.nextInt(256)}.${Random.nextInt(256)}.${Random.nextInt(1, 255)}"

    private fun register(email: String) =
        mvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = json.writeValueAsString(mapOf("email" to email, "password" to password))
        }.andExpect { status { isAccepted() } }

    private fun login(email: String, ip: String, pwd: String = password): ResultActionsDsl =
        mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            header(ClientIpResolver.SIMULATED_IP_HEADER, ip)
            content = json.writeValueAsString(mapOf("email" to email, "password" to pwd))
        }

    @Test
    fun `usuario normal recibe ALLOW y el intento queda registrado`() {
        val email = newEmail()
        val ip = newIp()
        register(email)

        login(email, ip).andExpect { status { isOk() } }

        val row = attempts.findByIpOrderByCreatedAtAsc(ip).single()
        assertEquals("ALLOW", row.zone)
        assertEquals(0.toShort(), row.score)
        assertTrue(row.triggeredRules.isEmpty())
        assertTrue(row.success)
        assertEquals(email, row.email)
    }

    @Test
    fun `ráfaga de credential stuffing desde una IP pasa a CHALLENGE y luego a BLOCK`() {
        val victim = newEmail()
        register(victim)
        val attackerIp = newIp()

        // 1-10: emails distintos con contraseña errónea. Desde el 6.º suma DISTINCT_USERS_PER_IP (25): sigue ALLOW.
        repeat(10) { login(newEmail(), attackerIp, "incorrecta-1234").andExpect { status { isUnauthorized() } } }

        // 11: con 10 intentos previos suma IP_VELOCITY (30) -> 55, CHALLENGE. Contraseña correcta: 403 sin tokens.
        val challenged = login(victim, attackerIp).andExpect {
            status { isForbidden() }
            jsonPath("$.error") { value("Verificación adicional requerida") }
            jsonPath("$.code") { value("CHALLENGE_REQUIRED") }
            jsonPath("$.challengeId") { isString() }
            jsonPath("$.accessToken") { doesNotExist() }
        }.andReturn()
        assertNull(challenged.response.getHeader(HttpHeaders.SET_COOKIE))

        // 12-20: en CHALLENGE la contraseña errónea también recibe 403 (no se revela cuál era correcta).
        repeat(9) { login(newEmail(), attackerIp, "incorrecta-1234").andExpect { status { isForbidden() } } }

        // 21: 20 intentos globales con 19 fallos suma GLOBAL_FAILURE_RATE (15) -> 70, BLOCK.
        // La contraseña correcta ya no se verifica, y la respuesta es idéntica para un email inexistente.
        val blocked = login(victim, attackerIp).andExpect { status { isTooManyRequests() } }.andReturn()
        val blockedMissing = login(newEmail(), attackerIp).andExpect { status { isTooManyRequests() } }.andReturn()
        assertEquals(blocked.response.contentAsString, blockedMissing.response.contentAsString)
        assertNull(blocked.response.getHeader(HttpHeaders.SET_COOKIE))
        val body = blocked.response.contentAsString
        assertFalse(body.contains("score") || body.contains("IP_VELOCITY") || body.contains("rule"))

        // Otra IP no queda bloqueada: solo suma la tasa global (15) -> ALLOW.
        login(victim, newIp()).andExpect { status { isOk() } }

        val rows = attempts.findByIpOrderByCreatedAtAsc(attackerIp)
        assertEquals(22, rows.size)
        assertEquals(List(10) { "ALLOW" } + List(10) { "CHALLENGE" } + List(2) { "BLOCK" }, rows.map { it.zone })
        assertTrue(rows.none { it.success })
        val lastBlocked = rows.last()
        assertEquals(70.toShort(), lastBlocked.score)
        assertEquals(
            listOf("IP_VELOCITY", "DISTINCT_USERS_PER_IP", "GLOBAL_FAILURE_RATE"),
            lastBlocked.triggeredRules.toList(),
        )
    }
}
