package com.authrisk.infrastructure.web

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration

/** Metadatos de OpenAPI y esquema Bearer para probar /api/auth/me desde Swagger (solo perfil dev). */
@Configuration
@OpenAPIDefinition(info = Info(title = "AuthShield API", version = "0.1.0", description = "Autenticación adaptativa (uso académico)"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig
