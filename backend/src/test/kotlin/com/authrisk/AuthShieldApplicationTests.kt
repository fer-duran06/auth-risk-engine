package com.authrisk

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifica que el contexto levanta contra PostgreSQL y Redis reales (Testcontainers). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthShieldApplicationTests : IntegrationTestBase() {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `health responde UP`() {
        val response = rest.getForEntity("/actuator/health", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("\"status\":\"UP\""))
    }

    @Test
    fun `rutas no declaradas se deniegan sin autenticación`() {
        // Con el resource server JWT, un anónimo recibe 401 (antes de Fase 1 era 403).
        val response = rest.getForEntity("/api/cualquier-cosa", String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
