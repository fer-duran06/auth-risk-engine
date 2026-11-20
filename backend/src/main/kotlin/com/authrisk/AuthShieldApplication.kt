package com.authrisk

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/**
 * Punto de entrada de AuthShield.
 * Se excluye el usuario en memoria por defecto de Spring Security para no generar ni loguear contraseñas.
 */
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConfigurationPropertiesScan
class AuthShieldApplication

fun main(args: Array<String>) {
    runApplication<AuthShieldApplication>(*args)
}
