package com.authrisk

import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.security.SecureRandom
import java.util.Base64

/**
 * Base de las pruebas de integración: PostgreSQL y Redis reales (Testcontainers), compartidos
 * por todas las clases para reutilizar el contexto de Spring. El secreto JWT y la clave TOTP se generan al azar.
 */
abstract class IntegrationTestBase {

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    /** Cada prueba parte sin contadores de riesgo: los intentos de otra no deben influir en su score. */
    @BeforeEach
    fun flushRiskCounters() {
        redisTemplate.execute { connection -> connection.serverCommands().flushAll() }
    }

    companion object {
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine").apply { start() }

        @ServiceConnection(name = "redis")
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379).apply { start() }

        private val jwtSecret: String =
            Base64.getEncoder().encodeToString(ByteArray(48).also(SecureRandom()::nextBytes))

        /** Clave AES-256 aleatoria para el secreto TOTP (base64 de 32 bytes). */
        val totpEncryptionKey: String =
            Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))

        @DynamicPropertySource
        @JvmStatic
        fun authProperties(registry: DynamicPropertyRegistry) {
            registry.add("app.auth.jwt-secret") { jwtSecret }
            registry.add("app.two-factor.encryption-key") { totpEncryptionKey }
            // Explícito para no depender del perfil activo: las pruebas varían la IP con X-Simulated-IP.
            registry.add("app.demo-mode") { "true" }
        }
    }
}
