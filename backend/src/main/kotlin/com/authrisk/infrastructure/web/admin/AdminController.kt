package com.authrisk.infrastructure.web.admin

import com.authrisk.application.admin.AdminStats
import com.authrisk.application.admin.EventPage
import com.authrisk.application.admin.GetAdminStatsUseCase
import com.authrisk.application.admin.GetRiskConfigUseCase
import com.authrisk.application.admin.IpActivity
import com.authrisk.application.admin.ListAdminEventsUseCase
import com.authrisk.application.admin.ListRecentIpsUseCase
import com.authrisk.application.admin.RiskConfigView
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Panel de administración (solo lectura). SecurityConfig exige ROLE_ADMIN para todo /api/admin.
 * Es el único lugar de la API que expone score y reglas disparadas.
 */
@Tag(name = "Administración")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/admin")
class AdminController(
    private val stats: GetAdminStatsUseCase,
    private val events: ListAdminEventsUseCase,
    private val ips: ListRecentIpsUseCase,
    private val riskConfig: GetRiskConfigUseCase,
) {

    @Operation(summary = "Resumen de las últimas 24 h: intentos por zona, sesiones, verificaciones 2FA y top de IPs.")
    @GetMapping("/stats")
    fun stats(): AdminStats = stats.execute()

    @Operation(summary = "Intentos de login (más recientes primero), con filtros por zona e IP. size máximo 100.")
    @GetMapping("/events")
    fun events(
        @RequestParam(required = false) zone: String?,
        @RequestParam(required = false) ip: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${ListAdminEventsUseCase.DEFAULT_SIZE}") size: Int,
    ): EventPage = events.execute(zone, ip, page, size)

    @Operation(summary = "IPs con intentos de login en las últimas 24 h, con la zona de su último intento.")
    @GetMapping("/ips")
    fun ips(@RequestParam(defaultValue = "${ListRecentIpsUseCase.DEFAULT_LIMIT}") limit: Int): List<IpActivity> =
        ips.execute(limit)

    @Operation(summary = "Pesos, umbrales y ventanas efectivos del motor de riesgo (solo lectura).")
    @GetMapping("/risk-config")
    fun riskConfig(): RiskConfigView = riskConfig.execute()
}
