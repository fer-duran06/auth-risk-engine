package com.authrisk.infrastructure.web

import com.authrisk.infrastructure.security.JwtTokenIssuer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Seguridad HTTP: API sin estado autenticada con JWT Bearer (resource server).
 * Solo los endpoints de autenticación, health y Swagger son públicos; lo no declarado se deniega.
 */
@Configuration
class SecurityConfig(
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>,
    @Value("\${app.demo-mode:false}") private val demoMode: Boolean,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // Sin sesiones ni formularios. El único dato en cookie es el refresh token, con SameSite=Strict
            // y Path=/api/auth: el navegador no lo envía en peticiones cross-site, lo que mitiga CSRF.
            .csrf { it.disable() }
            .cors { }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                it.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                it.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                    .permitAll()
                it.requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/auth/2fa/status").authenticated()
                it.requestMatchers(HttpMethod.POST, "/api/auth/2fa/setup", "/api/auth/2fa/enable").authenticated()
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
