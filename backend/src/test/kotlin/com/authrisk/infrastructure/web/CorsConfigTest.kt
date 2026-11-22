package com.authrisk.infrastructure.web

import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorsConfigTest {

    private val origin = "http://localhost:3000"

    private fun corsConfig(demoMode: Boolean) =
        SecurityConfig(allowedOrigins = listOf(origin), demoMode = demoMode)
            .corsConfigurationSource()
            .getCorsConfiguration(MockHttpServletRequest("OPTIONS", "/api/auth/login"))!!

    /** Simula el preflight del navegador pidiendo los headers indicados. */
    private fun allowedRequestHeaders(demoMode: Boolean, vararg headers: String) =
        corsConfig(demoMode).checkHeaders(headers.toList())

    @Test
    fun `sin modo demo el preflight rechaza X-Simulated-IP`() {
        assertNull(allowedRequestHeaders(false, ClientIpResolver.SIMULATED_IP_HEADER))
        assertEquals(listOf("Content-Type"), allowedRequestHeaders(false, "Content-Type"))
    }

    @Test
    fun `con modo demo el preflight acepta X-Simulated-IP`() {
        val allowed = allowedRequestHeaders(true, "Content-Type", ClientIpResolver.SIMULATED_IP_HEADER)
        assertEquals(listOf("Content-Type", ClientIpResolver.SIMULATED_IP_HEADER), allowed)
    }

    @Test
    fun `el origen sigue restringido y con credenciales`() {
        val config = corsConfig(true)
        assertEquals(origin, config.checkOrigin(origin))
        assertNull(config.checkOrigin("http://evil.example.com"))
        assertTrue(config.allowCredentials == true)
        assertTrue(config.checkHttpMethod(HttpMethod.POST)!!.contains(HttpMethod.POST))
    }
}
