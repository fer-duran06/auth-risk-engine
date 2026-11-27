package com.authrisk.infrastructure.web

import com.authrisk.infrastructure.redis.RedisRateLimiter
import com.authrisk.infrastructure.web.auth.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Límite de solicitudes por IP en los endpoints de autenticación más sensibles a volumen
 * (registro, login, refresh, 2FA). Corre dentro de la cadena de Spring Security (ver
 * [SecurityConfig]), antes de CORS y de la autenticación, para rechazar el exceso lo antes posible.
 *
 * No se registra como filtro de servlet genérico (ver el `FilterRegistrationBean` deshabilitado en
 * [SecurityConfig]): solo se ejecuta como parte de la cadena de Spring Security.
 */
class RateLimitFilter(
    private val limiter: RedisRateLimiter,
    private val clientIp: ClientIpResolver,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method == HttpMethod.OPTIONS.name() || !LIMITED_PATHS.any { request.requestURI == it }

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val result = limiter.check(clientIp.resolve(request))
        if (!result.allowed) {
            // Sin más detalle que el 429 genérico: misma política de no-filtrado que el resto de la API.
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.setHeader("Retry-After", result.retryAfterSeconds.toString())
            objectMapper.writeValue(response.writer, ErrorResponse(TOO_MANY_REQUESTS_MESSAGE))
            return
        }
        chain.doFilter(request, response)
    }

    companion object {
        const val TOO_MANY_REQUESTS_MESSAGE = "Demasiadas solicitudes. Intenta más tarde."

        val LIMITED_PATHS = setOf(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/2fa/verify",
            "/api/auth/2fa/setup",
            "/api/auth/2fa/enable",
        )
    }
}
