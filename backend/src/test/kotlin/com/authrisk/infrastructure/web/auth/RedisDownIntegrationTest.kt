package com.authrisk.infrastructure.web.auth

import com.authrisk.IntegrationTestBase
import com.authrisk.domain.port.TotpSecretCipher
import com.authrisk.domain.risk.RiskAssessment
import com.authrisk.domain.totp.Totp
import com.authrisk.infrastructure.persistence.UserJpaRepository
import com.authrisk.infrastructure.persistence.LoginAttemptJpaRepository
import com.authrisk.infrastructure.web.ClientIpResolver
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.net.ServerSocket
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Redis caído: contexto propio cuyo Redis apunta a un puerto cerrado (conexión rechazada real).
 * No hereda de [IntegrationTestBase] para no recibir su Redis; reutiliza solo su PostgreSQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RedisDownIntegrationTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var attempts: LoginAttemptJpaRepository

    private val password = "contrasena-larga-1"

    private fun post(path: String, email: String, pwd: String, ip: String) =
        mvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            header(ClientIpResolver.SIMULATED_IP_HEADER, ip)
            content = json.writeValueAsString(mapOf("email" to email, "password" to pwd))
        }

    @Test
    fun `con Redis caído el login correcto exige verificación y no emite tokens`() {
        val email = "user-${UUID.randomUUID()}@example.com"
        val ip = "10.255.0.1"
        post("/api/auth/register", email, password, ip).andExpect { status { isAccepted() } }

        val result = post("/api/auth/login", email, password, ip).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("CHALLENGE_REQUIRED") }
        }.andReturn()
        assertNull(result.response.getHeader(HttpHeaders.SET_COOKIE))

        // La contraseña incorrecta recibe el mismo 403: en CHALLENGE no se revela cuál es correcta.
        post("/api/auth/login", email, "incorrecta-1234", ip).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("CHALLENGE_REQUIRED") }
        }

        val rows = attempts.findByIpOrderByCreatedAtAsc(ip).filter { it.email == email }
        assertEquals(2, rows.size)
        rows.forEach {
            assertEquals("CHALLENGE", it.zone)
            assertNull(it.score)
            assertEquals(listOf(RiskAssessment.COUNTERS_UNAVAILABLE), it.triggeredRules.toList())
        }
    }

    @Autowired lateinit var users: UserJpaRepository
    @Autowired lateinit var cipher: TotpSecretCipher
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `con Redis caído el desafío vive en PostgreSQL y un usuario con 2FA puede completarlo`() {
        val email = "user-${UUID.randomUUID()}@example.com"
        val ip = "10.255.0.2"
        post("/api/auth/register", email, password, ip).andExpect { status { isAccepted() } }
        // Sin Redis todo login es CHALLENGE, así que el 2FA se activa directamente en BD.
        val userId = users.findByEmail(email)!!.id
        val secret = Totp.newSecret()
        jdbc.update(
            "INSERT INTO user_totp (user_id, secret_encrypted, enabled, confirmed_at, created_at) VALUES (?, ?, TRUE, now(), now())",
            userId, cipher.encrypt(userId, secret),
        )

        val challengeId = json.readTree(
            post("/api/auth/login", email, password, ip).andExpect { status { isForbidden() } }.andReturn().response.contentAsString,
        )["challengeId"].asText()
        val totp = Totp()
        mvc.post("/api/auth/2fa/verify") {
            contentType = MediaType.APPLICATION_JSON
            header(ClientIpResolver.SIMULATED_IP_HEADER, ip)
            content = json.writeValueAsString(
                mapOf("challengeId" to challengeId, "code" to totp.generate(secret, totp.stepAt(Instant.now()))),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isString() }
        }
    }

    companion object {
        // Puerto libre que se cierra enseguida: nadie escucha ahí.
        private val closedPort = ServerSocket(0).use { it.localPort }
        private val jwtSecret = Base64.getEncoder().encodeToString(ByteArray(48).also(SecureRandom()::nextBytes))

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            val postgres = IntegrationTestBase.postgres
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { "127.0.0.1" }
            registry.add("spring.data.redis.port") { closedPort }
            registry.add("app.auth.jwt-secret") { jwtSecret }
            registry.add("app.two-factor.encryption-key") { IntegrationTestBase.totpEncryptionKey }
            registry.add("app.demo-mode") { "true" }
        }
    }
}
