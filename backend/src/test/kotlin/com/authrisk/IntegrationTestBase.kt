package com.authrisk

import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.security.SecureRandom
import java.util.Base64

/**
 * Base de las pruebas de integración: PostgreSQL y Redis reales (Testcontainers), compartidos
 * por todas las clases para reutilizar el contexto de Spring. El secreto JWT se genera al azar.
 */
abstract class IntegrationTestBase {

    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine").apply { start() }

        @ServiceConnection(name = "redis")
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379).apply { start() }

        private val jwtSecret: String =
            Base64.getEncoder().encodeToString(ByteArray(48).also(SecureRandom()::nextBytes))

        @DynamicPropertySource
        @JvmStatic
        fun authProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.auth.jwt-secret") { jwtSecret }
        }
    }
}
