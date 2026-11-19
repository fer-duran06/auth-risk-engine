package com.authrisk

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpStatus
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifica que el contexto levanta contra PostgreSQL y Redis reales (Testcontainers). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthShieldApplicationTests {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @ServiceConnection(name = "redis")
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    }

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `health responde UP`() {
        val response = rest.getForEntity("/actuator/health", String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("\"status\":\"UP\""))
    }

    @Test
    fun `rutas no publicas se deniegan`() {
        val response = rest.getForEntity("/api/cualquier-cosa", String::class.java)
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    }
}
