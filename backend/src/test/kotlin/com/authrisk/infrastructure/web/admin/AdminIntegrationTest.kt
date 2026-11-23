package com.authrisk.infrastructure.web.admin

import com.authrisk.IntegrationTestBase
import com.authrisk.infrastructure.web.ClientIpResolver
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Panel de administración con PostgreSQL real: autorización por rol y consultas sobre datos conocidos. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var json: ObjectMapper
    @Autowired lateinit var jdbc: JdbcTemplate

    private val password = "contrasena-larga-1"
    private lateinit var adminToken: String
    private lateinit var userToken: String

    private val endpoints = listOf("/api/admin/stats", "/api/admin/events", "/api/admin/ips", "/api/admin/risk-config")

    /** Igual que en la vida real: registro, promoción por SQL y login nuevo (el rol viaja en el JWT). */
    private fun tokenFor(admin: Boolean): String {
        val email = "user-${UUID.randomUUID()}@example.com"
        val body = json.writeValueAsString(mapOf("email" to email, "password" to password))
        mvc.post("/api/auth/register") { contentType = MediaType.APPLICATION_JSON; content = body }
        if (admin) jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email)
        val res = mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = body
            header(ClientIpResolver.SIMULATED_IP_HEADER, "10.9.${Random.nextInt(256)}.${Random.nextInt(1, 255)}")
        }.andExpect { status { isOk() } }.andReturn()
        return json.readTree(res.response.contentAsString)["accessToken"].asText()
    }

    private fun insert(ip: String, minutesAgo: Long, zone: String, score: Int?, success: Boolean, stage: String = "PASSWORD", rules: String = "{}") {
        jdbc.update(
            """
            INSERT INTO login_attempts (id, email, ip, score, zone, triggered_rules, success, created_at, stage)
            VALUES (?, ?, ?, ?, ?, ?::text[], ?, now() - make_interval(mins => ?), ?)
            """,
            UUID.randomUUID(), "v@example.com", ip, score, zone, rules, success, minutesAgo.toInt(), stage,
        )
    }

    private fun get(path: String, token: String? = adminToken) =
        mvc.get(path) { token?.let { header("Authorization", "Bearer $it") } }

    private fun body(path: String): JsonNode =
        json.readTree(get(path).andExpect { status { isOk() } }.andReturn().response.contentAsString)

    @BeforeEach
    fun data() {
        adminToken = tokenFor(admin = true)
        userToken = tokenFor(admin = false)
        // Datos conocidos: se descartan los intentos de otras pruebas (y los logins de arriba).
        jdbc.update("DELETE FROM login_attempts")
        insert(IP_A, 180, "ALLOW", 0, true)
        insert(IP_A, 120, "CHALLENGE", 40, false, rules = "{DISTINCT_USERS_PER_IP,GLOBAL_FAILURE_RATE}")
        insert(IP_A, 60, "BLOCK", 70, false, rules = "{IP_VELOCITY,DISTINCT_USERS_PER_IP,GLOBAL_FAILURE_RATE}")
        insert(IP_B, 30, "ALLOW", 0, true)
        insert(IP_B, 25, "CHALLENGE", 30, false, stage = "TOTP")
        insert(IP_B, 20, "CHALLENGE", 30, true, stage = "TOTP")
        insert(IP_D, 10, "CHALLENGE", null, false, rules = "{COUNTERS_UNAVAILABLE}")
        insert(IP_C, 30 * 60, "ALLOW", 0, false) // fuera de la ventana de 24 h
    }

    @Test
    fun `anónimo 401, USER 403 y ADMIN 200 en cada endpoint`() {
        endpoints.forEach { path ->
            get(path, token = null).andExpect { status { isUnauthorized() } }
            get(path, token = userToken).andExpect { status { isForbidden() } }
            get(path).andExpect { status { isOk() } }
        }
        // Solo lectura: otros métodos siguen denegados también para el admin.
        mvc.post("/api/admin/stats") { header("Authorization", "Bearer $adminToken") }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `stats separa login y 2FA, cuenta por zona y ordena el top de IPs`() {
        val s = body("/api/admin/stats")
        assertEquals(5, s["loginAttempts"].asInt())
        assertEquals(listOf(2, 2, 1), listOf("allow", "challenge", "block").map { s["byZone"][it].asInt() })
        assertEquals(3, s["loginWithoutTokens"].asInt())
        assertEquals(3, s["sessionsIssued"].asInt())
        assertEquals(1, s["totpSuccesses"].asInt())
        assertEquals(1, s["totpFailures"].asInt())
        assertEquals(listOf(IP_A to 3, IP_B to 1, IP_D to 1), s["topIps"].map { it["ip"].asText() to it["attempts"].asInt() })
    }

    @Test
    fun `events pagina por fecha descendente y filtra por zona e IP`() {
        val all = body("/api/admin/events")
        assertEquals(8, all["total"].asInt())
        assertEquals(listOf(IP_D, IP_B, IP_B, IP_B, IP_A, IP_A, IP_A, IP_C), all["items"].map { it["ip"].asText() })
        val first = all["items"][0]
        assertTrue(first["score"].isNull)
        assertEquals(listOf("COUNTERS_UNAVAILABLE"), first["triggeredRules"].map { it.asText() })
        assertEquals("TOTP", all["items"][1]["stage"].asText())

        val blocked = body("/api/admin/events?zone=BLOCK")
        assertEquals(1, blocked["total"].asInt())
        assertEquals(70, blocked["items"][0]["score"].asInt())
        assertEquals(3, blocked["items"][0]["triggeredRules"].size())

        val byIp = body("/api/admin/events?ip=$IP_A&zone=challenge")
        assertEquals(1, byIp["total"].asInt())

        val page2 = body("/api/admin/events?page=2&size=3")
        assertEquals(3, page2["totalPages"].asInt())
        assertEquals(listOf(IP_A, IP_C), page2["items"].map { it["ip"].asText() })
        assertEquals(0, body("/api/admin/events?page=9&size=3")["items"].size())
    }

    @Test
    fun `ips lista la actividad de 24 h con la zona del último intento`() {
        val ips = body("/api/admin/ips")
        assertEquals(listOf(IP_D, IP_B, IP_A), ips.map { it["ip"].asText() })
        val a = ips[2]
        assertEquals(3, a["attempts"].asInt())
        assertEquals(2, a["failures"].asInt())
        assertEquals("BLOCK", a["lastZone"].asText())
        assertEquals(70, a["lastScore"].asInt())
        assertTrue(ips[0]["lastScore"].isNull)
        // B: solo su login cuenta (los verify 2FA no son intentos de login).
        assertEquals(1, ips[1]["attempts"].asInt())
        assertEquals(1, body("/api/admin/ips?limit=1").size())
    }

    @Test
    fun `risk-config refleja la configuración efectiva`() {
        val c = body("/api/admin/risk-config")
        assertTrue(c["enabled"].asBoolean())
        assertEquals(listOf(30, 70, 100), listOf("challengeThreshold", "blockThreshold", "maxScore").map { c[it].asInt() })
        assertEquals(
            listOf("IP_VELOCITY" to 30, "DISTINCT_USERS_PER_IP" to 25, "ACCOUNT_FAILURES" to 20, "GLOBAL_FAILURE_RATE" to 15),
            c["rules"].map { it["id"].asText() to it["weight"].asInt() },
        )
        assertEquals(listOf(60L, 600L, 900L, 300L), c["rules"].map { it["windowSeconds"].asLong() })
        assertEquals(0.5, c["rules"][3]["threshold"].asDouble())
        assertEquals(20, c["rules"][3]["minAttempts"].asInt())
    }

    @Test
    fun `parámetros inválidos dan 400 genérico sin repetir el valor`() {
        listOf(
            "/api/admin/events?size=0", "/api/admin/events?size=101", "/api/admin/events?page=-1",
            "/api/admin/events?page=abc", "/api/admin/events?zone=PELIGRO", "/api/admin/events?ip=${"9".repeat(46)}",
            "/api/admin/ips?limit=0", "/api/admin/ips?limit=101",
        ).forEach { path ->
            val res = get(path).andExpect { status { isBadRequest() } }.andReturn().response.contentAsString
            assertFalse(res.contains("PELIGRO") || res.contains("abc"), path)
        }
    }

    @Test
    fun `los endpoints de usuario siguen sin exponer score ni reglas`() {
        val me = get("/api/auth/me", userToken).andReturn().response.contentAsString
        assertFalse(me.contains("score") || me.contains("triggeredRules"))
    }

    companion object {
        const val IP_A = "198.51.100.1"
        const val IP_B = "198.51.100.2"
        const val IP_C = "198.51.100.3"
        const val IP_D = "198.51.100.4"
    }
}
