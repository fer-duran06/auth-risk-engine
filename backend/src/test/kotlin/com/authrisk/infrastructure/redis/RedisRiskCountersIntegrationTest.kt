package com.authrisk.infrastructure.redis

import com.authrisk.IntegrationTestBase
import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.RiskSignals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Semántica de los contadores contra un Redis real. */
@SpringBootTest
class RedisRiskCountersIntegrationTest : IntegrationTestBase() {

    @Autowired lateinit var counters: RedisRiskCounters
    @Autowired lateinit var redis: StringRedisTemplate

    @Test
    fun `cuenta por IP, por cuenta y global según el resultado`() {
        val ip = "10.1.1.1"
        counters.record(ip, "a@example.com", LoginOutcome.FAILURE)
        counters.record(ip, "a@example.com", LoginOutcome.FAILURE)
        counters.record(ip, "b@example.com", LoginOutcome.SUCCESS)
        counters.record(ip, "c@example.com", LoginOutcome.CHALLENGED)
        counters.record(ip, "d@example.com", LoginOutcome.BLOCKED)

        // Los bloqueados cuentan para la IP y emails distintos, pero no para la cuenta ni lo global.
        // Solo "a" tuvo un FAILURE, así que la cuenta global de cuentas distintas con fallo es 1
        // (es un contador verdaderamente global: no cambia según la IP o el email consultados).
        assertEquals(RiskSignals(5, 4, 2, 4, 2, 1), counters.read(ip, "a@example.com"))
        assertEquals(RiskSignals(0, 0, 0, 4, 2, 1), counters.read("10.2.2.2", "d@example.com"))
    }

    @Test
    fun `los emails no se guardan en claro en Redis`() {
        counters.record("10.1.1.2", "secreto@example.com", LoginOutcome.FAILURE)

        val keys = redis.keys("risk:*")
        assertTrue(keys.isNotEmpty())
        assertTrue(keys.none { it.contains("secreto") })
        val members = redis.opsForZSet().range("risk:ip:10.1.1.2:emails", 0, -1)!!
        assertTrue(members.single().matches(Regex("^[0-9a-f]{64}$")))
    }
}
