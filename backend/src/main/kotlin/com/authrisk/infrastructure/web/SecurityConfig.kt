package com.authrisk.infrastructure.web

import com.authrisk.infrastructure.redis.RedisRateLimiter
import com.authrisk.infrastructure.security.JwtTokenIssuer
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.security.web.session.DisableEncodeUrlFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Seguridad HTTP: API sin estado autenticada con JWT Bearer (resource server).
 * Solo los endpoints de autenticación, health y Swagger son públicos; todo /api/admin exige ROLE_ADMIN
 * y lo no declarado se deniega.
 */
@Configuration
class SecurityConfig(
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>,
    @Value("\${app.demo-mode:false}") private val demoMode: Boolean,
) {

    /**
     * El filtro de rate limiting solo debe correr dentro de la cadena de Spring Security
     * (añadido más abajo con `addFilterBefore`). Este bean existe para que Spring Boot no lo
     * registre además como filtro de servlet genérico (lo que lo ejecutaría dos veces).
     */
    @Bean
    fun rateLimitFilterRegistration(filter: RateLimitFilter): FilterRegistrationBean<RateLimitFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun rateLimitFilter(limiter: RedisRateLimiter, clientIp: ClientIpResolver, objectMapper: ObjectMapper): RateLimitFilter =
        RateLimitFilter(limiter, clientIp, objectMapper)

    @Bean
    fun securityFilterChain(http: HttpSecurity, rateLimitFilter: RateLimitFilter): SecurityFilterChain {
        http
            // Antes que cualquier otro filtro de la cadena (incluido el de CORS/JWT):
            // una solicitud que excede el límite se corta de inmediato.
            .addFilterBefore(rateLimitFilter, DisableEncodeUrlFilter::class.java)
            // Sin sesiones ni formularios. El único dato en cookie es el refresh token, con SameSite=Strict
            // y Path=/api/auth: el navegador no lo envía en peticiones cross-site, lo que mitiga CSRF.
            .csrf { it.disable() }
            .cors { }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // API pura JSON: CSP restrictiva, sin marcos, sin filtrar el referer entre orígenes.
            // HSTS solo se envía cuando la petición llega como HTTPS (ver forward-headers-strategy
            // en application.yml, que repara isSecure() detrás del proxy de despliegue).
            .headers { headers ->
                headers
                    .contentSecurityPolicy { it.policyDirectives("default-src 'none'; frame-ancestors 'none'") }
                    .httpStrictTransportSecurity { it.includeSubDomains(true).maxAgeInSeconds(31536000) }
                    .frameOptions { it.deny() }
                    .contentTypeOptions { }
                    .referrerPolicy { it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER) }
            }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                it.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                    "/api/auth/2fa/verify")
                    .permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/auth/2fa/status", "/api/auth/breach-status", "/api/notifications")
                    .authenticated()
                it.requestMatchers(HttpMethod.POST, "/api/auth/2fa/setup", "/api/auth/2fa/enable").authenticated()
                it.requestMatchers(HttpMethod.POST, "/api/notifications/*/read").authenticated()
                // Panel de administración: solo lectura y solo ROLE_ADMIN (claim role del JWT).
                it.requestMatchers(HttpMethod.GET, "/api/admin/**").hasRole("ADMIN")
                it.requestMatchers("/error").permitAll()
                it.anyRequest().denyAll()
            }
            .oauth2ResourceServer { rs -> rs.jwt { it.jwtAuthenticationConverter(jwtAuthenticationConverter()) } }
        return http.build()
    }

    /** Traduce el claim `role` a la autoridad ROLE_<rol>. */
    private fun jwtAuthenticationConverter() = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter(
            JwtGrantedAuthoritiesConverter().apply {
                setAuthoritiesClaimName(JwtTokenIssuer.ROLE_CLAIM)
                setAuthorityPrefix("ROLE_")
            },
        )
    }

    /**
     * CORS restringido a los orígenes configurados (por defecto solo el frontend local).
     * `X-Simulated-IP` solo se admite en modo demo; fuera de él el preflight lo rechaza.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            this.allowedOrigins = this@SecurityConfig.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = buildList {
                add("Authorization")
                add("Content-Type")
                if (demoMode) add(ClientIpResolver.SIMULATED_IP_HEADER)
            }
            // Necesario para que el navegador envíe la cookie de refresh desde el frontend.
            allowCredentials = true
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }
}
