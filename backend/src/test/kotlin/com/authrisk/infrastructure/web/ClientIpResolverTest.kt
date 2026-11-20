package com.authrisk.infrastructure.web

import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClientIpResolverTest {

    private fun request(simulated: String? = null) = MockHttpServletRequest().apply {
        remoteAddr = "192.0.2.1"
        addHeader("X-Forwarded-For", "6.6.6.6")
        simulated?.let { addHeader(ClientIpResolver.SIMULATED_IP_HEADER, it) }
    }

    @Test
    fun `sin modo demo se usa la conexión e ignora los headers`() {
        val resolver = ClientIpResolver(demoMode = false)
        assertEquals("192.0.2.1", resolver.resolve(request()))
        assertEquals("192.0.2.1", resolver.resolve(request(simulated = "10.0.0.5")))
    }

    @Test
    fun `con modo demo se acepta X-Simulated-IP válido`() {
        val resolver = ClientIpResolver(demoMode = true)
        assertEquals("10.0.0.5", resolver.resolve(request(simulated = "10.0.0.5")))
        assertEquals("2001:db8:0:0:0:0:0:1", resolver.resolve(request(simulated = "2001:db8::1")))
        // Nunca X-Forwarded-For.
        assertEquals("192.0.2.1", resolver.resolve(request()))
    }

    @Test
    fun `X-Simulated-IP inválido cae a la IP de la conexión`() {
        val resolver = ClientIpResolver(demoMode = true)
        listOf("localhost", "evil.example.com", "1.2.3.999", "1.2.3", "", "10.0.0.1, 10.0.0.2", "fe80::1%eth0").forEach {
            assertEquals("192.0.2.1", resolver.resolve(request(simulated = it)), "valor: $it")
        }
    }

    @Test
    fun `solo se aceptan literales IP, sin resolver nombres`() {
        assertEquals("127.0.0.1", ClientIpResolver.parseIpLiteral("127.0.0.1"))
        assertEquals("0:0:0:0:0:0:0:1", ClientIpResolver.parseIpLiteral("::1"))
        assertNull(ClientIpResolver.parseIpLiteral("example.com"))
        assertNull(ClientIpResolver.parseIpLiteral("cafe"))
        assertNull(ClientIpResolver.parseIpLiteral("abc::xyz"))
        assertNull(ClientIpResolver.parseIpLiteral(null))
    }
}
