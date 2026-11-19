package com.authrisk.lab

import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Código de laboratorio para la práctica SAST. Solo se activa con el perfil "lab". */
@Profile("lab")
@RestController
@RequestMapping("/lab/users")
class LabUserController(private val jdbcTemplate: JdbcTemplate) {

    @GetMapping
    fun buscarPorEmail(@RequestParam email: String): List<Map<String, Any>> {
        // VULNERABILIDAD INTENCIONAL (práctica SAST #1): Inyección SQL (CWE-89)
        val sql = "SELECT id, email FROM users WHERE email = '" + email + "'"
        return jdbcTemplate.queryForList(sql)
    }
}
