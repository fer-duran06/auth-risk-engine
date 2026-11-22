package com.authrisk.infrastructure.web.auth

import com.authrisk.IntegrationTestBase
import com.authrisk.domain.totp.Base32
import com.authrisk.domain.totp.Totp
import com.authrisk.infrastructure.persistence.LoginAttemptJpaRepository
import com.authrisk.infrastructure.scheduling.LoginChallengeCleanup
import com.authrisk.infrastructure.web.ClientIpResolver
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.security.MessageDigest
import java.time.Instant
import java.util.HexFormat
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Zona CHALLENGE con 2FA de punta a punta (PostgreSQL y Redis reales). */
@SpringBootTest
@AutoConfigureMockMvc
class TwoFactorChallengeIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var attempts: LoginAttemptJpaRepository
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var cleanup: LoginChallengeCleanup

    private val totp = Totp()
    private val password = "contrasena-larga-1"
    private fun newEmail() = "user-${UUID.randomUUID()}@example.com"
    private fun newIp() = "10.${Random.nextInt(256)}.${Random.nextInt(256)}.${Random.nextInt(1, 255)}"

    private fun post(path: String, body: Map<String, String>, ip: String? = null, token: String? = null) = mvc.post(path) {
        contentType = MediaType.APPLICATION_JSON
        content = json.writeValueAsString(body)
        ip?.let { header(ClientIpResolver.SIMULATED_IP_HEADER, it) }
        token?.let { header("Authorization", "Bearer $it") }
    }

    private fun MvcResult.body(): JsonNode = json.readTree(response.contentAsString)

    private fun login(email: String, ip: String, pwd: String = password) =
        post("/api/auth/login", mapOf("email" to email, "password" to pwd), ip)

    private fun verify(challengeId: String, code: String, ip: String) =
        post("/api/auth/2fa/verify", mapOf("challengeId" to challengeId, "code" to code), ip)

    /** Usuario registrado con 2FA activo. Devuelve su email y el secreto Base32. */
    private fun userWith2fa(): Pair<String, String> {
        val email = newEmail()
        post("/api/auth/register", mapOf("email" to email, "password" to password)).andExpect { status { isAccepted() } }
        val token = login(email, newIp()).andExpect { status { isOk() } }.andReturn().body()["accessToken"].asText()
        val secret = post("/api/auth/2fa/setup", emptyMap(), token = token).andReturn().body()["secret"].asText()
        // El código de la activación consume el paso actual: los de login usan el siguiente paso válido.
        post("/api/auth/2fa/enable", mapOf("code" to code(secret)), token = token).andExpect { status { isNoContent() } }
        return email to secret
    }

    private fun code(secret: String, offset: Long = 0) =
        totp.generate(Base32.decode(secret), totp.stepAt(Instant.now()) + offset)

    /** IP con 10 intentos previos (IP_VELOCITY, +30): el siguiente login cae en CHALLENGE. */
    private fun challengeIp(): String {
        val ip = newIp()
        val decoy = newEmail()
        repeat(10) { login(decoy, ip, "incorrecta-1234").andExpect { status { isUnauthorized() } } }
        return ip
    }

    private fun challengeId(email: String, ip: String, pwd: String = password): String =
        login(email, ip, pwd).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("CHALLENGE_REQUIRED") }
        }.andReturn().body()["challengeId"].asText()

    private fun sha256(s: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()))

    private fun rejected(challengeId: String, code: String, ip: String): String =
        verify(challengeId, code, ip).andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("Credenciales inválidas") }
        }.andReturn().also { assertNull(it.response.getHeader(HttpHeaders.SET_COOKIE)) }.response.contentAsString

    @Test
    fun `flujo completo setup, enable, login en CHALLENGE y verify exitoso`() {
        val (email, secret) = userWith2fa()
        val ip = challengeIp()
        val id = challengeId(email, ip)

        // El siguiente paso: el actual ya se usó al activar (puede haber cambiado de paso entre medio).
        val res = verify(id, code(secret, offset = 1), ip).andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { isString() }
            cookie { exists(RefreshCookieFactory.NAME); httpOnly(RefreshCookieFactory.NAME, true) }
        }.andReturn()
        mvc.get("/api/auth/me") { header("Authorization", "Bearer ${res.body()["accessToken"].asText()}") }
            .andExpect { status { isOk() }; jsonPath("$.email") { value(email) } }

        // Registro del administrador: el login en CHALLENGE y el verify con stage TOTP.
        val rows = attempts.findByIpOrderByCreatedAtAsc(ip).takeLast(2)
        assertEquals(listOf("PASSWORD", "TOTP"), rows.map { it.stage })
        assertEquals(listOf("CHALLENGE", "CHALLENGE"), rows.map { it.zone })
        assertEquals(listOf(false, true), rows.map { it.success })
        assertEquals(rows[0].score, rows[1].score)

        // En BD solo está el hash del challengeId, y ya quedó cerrado.
        val consumed = jdbc.queryForObject(
            "SELECT consumed_at IS NOT NULL FROM login_challenges WHERE id_hash = ?", Boolean::class.java, sha256(id),
        )
        assertEquals(true, consumed)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM login_challenges WHERE id_hash = ?", Int::class.java, id))
    }

    @Test
    fun `respuesta idéntica con contraseña correcta, incorrecta y email inexistente`() {
        val (email, _) = userWith2fa()
        val ip = challengeIp()

        val responses = listOf(
            login(email, ip).andReturn(),
            login(email, ip, "incorrecta-1234").andReturn(),
            login(newEmail(), ip).andReturn(),
        )

        responses.forEach {
            assertEquals(403, it.response.status)
            assertNull(it.response.getHeader(HttpHeaders.SET_COOKIE))
        }
        val bodies = responses.map { it.body() }
        // Mismos campos y valores salvo el challengeId, que es aleatorio y del mismo largo.
        assertEquals(1, bodies.map { b -> b.fieldNames().asSequence().toList() }.toSet().size)
        assertEquals(1, bodies.map { b -> b["error"].asText() to b["code"].asText() }.toSet().size)
        assertEquals(1, bodies.map { b -> b["challengeId"].asText().length }.toSet().size)
        assertEquals(3, bodies.map { b -> b["challengeId"].asText() }.toSet().size)
        assertEquals(1, responses.map { it.response.contentType }.toSet().size)
    }

    @Test
    fun `contraseña incorrecta con código válido no emite tokens, con el mismo 401`() {
        val (email, secret) = userWith2fa()
        val ip = challengeIp()

        val wrongPassword = rejected(challengeId(email, ip, "incorrecta-1234"), code(secret, 1), ip)
        val unknown = rejected("no-existe", code(secret, 1), ip)
        assertEquals(wrongPassword, unknown)
    }

    @Test
    fun `código incorrecto y código reutilizado se rechazan`() {
        val (email, secret) = userWith2fa()
        val ip = challengeIp()
        val id = challengeId(email, ip)
        val good = code(secret, 1)
        val wrong = if (good == "000000") "111111" else "000000"

        rejected(id, wrong, ip)
        verify(id, good, ip).andExpect { status { isOk() } }

        // El mismo código en un desafío nuevo ya no sirve (anti-reutilización).
        rejected(challengeId(email, ip), good, ip)
    }

    @Test
    fun `challengeId reutilizado o vencido se rechaza`() {
        val (email, secret) = userWith2fa()
        val ip = challengeIp()

        val used = challengeId(email, ip)
        verify(used, code(secret, 1), ip).andExpect { status { isOk() } }
        rejected(used, code(secret, 1), ip)

        // Otro usuario, para que el código siga sin usar: solo el vencimiento explica el rechazo.
        val (other, otherSecret) = userWith2fa()
        val expired = challengeId(other, ip)
        jdbc.update("UPDATE login_challenges SET expires_at = now() - interval '1 second' WHERE id_hash = ?", sha256(expired))
        rejected(expired, code(otherSecret, 1), ip)
    }

    @Test
    fun `máximo 5 intentos por challengeId`() {
        val (email, secret) = userWith2fa()
        val ip = challengeIp()
        val id = challengeId(email, ip)
        val good = code(secret, 1)
        val wrong = if (good == "000000") "111111" else "000000"

        repeat(5) { rejected(id, wrong, ip) }
        rejected(id, good, ip)

        val row = jdbc.queryForMap("SELECT attempts, consumed_at FROM login_challenges WHERE id_hash = ?", sha256(id))
        assertEquals(5, (row["attempts"] as Number).toInt())
        assertNotNull(row["consumed_at"])
        val verifies = attempts.findByIpOrderByCreatedAtAsc(ip).filter { it.stage == "TOTP" }
        assertEquals(6, verifies.size)
        assertTrue(verifies.none { it.success })
        assertTrue("CHALLENGE_INVALID" in verifies.last().triggeredRules)
    }

    @Test
    fun `usuario sin 2FA activo no puede completar el desafío`() {
        val email = newEmail()
        post("/api/auth/register", mapOf("email" to email, "password" to password))
        val ip = challengeIp()
        val id = challengeId(email, ip)
        rejected(id, "123456", ip)
    }

    @Test
    fun `la purga programada borra desafíos vencidos y cerrados`() {
        val (email, secret) = userWith2fa()
        val ip = challengeIp()
        val closed = challengeId(email, ip)
        verify(closed, code(secret, 1), ip).andExpect { status { isOk() } }
        val expired = challengeId(email, ip)
        jdbc.update("UPDATE login_challenges SET expires_at = now() - interval '1 second' WHERE id_hash = ?", sha256(expired))
        val open = challengeId(email, ip)

        cleanup.run()

        fun exists(id: String) =
            jdbc.queryForObject("SELECT count(*) FROM login_challenges WHERE id_hash = ?", Int::class.java, sha256(id)) == 1
        assertFalse(exists(closed))
        assertFalse(exists(expired))
        assertTrue(exists(open))
        assertNotEquals(closed, open)
    }
}
