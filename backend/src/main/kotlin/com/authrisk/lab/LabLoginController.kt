package com.authrisk.lab

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Código de laboratorio para la práctica SAST. Solo se activa con el perfil "lab". */
@Profile("lab")
@RestController
@RequestMapping("/lab/login")
class LabLoginController(
    private val hasher: LabPasswordHasher,
    private val signer: LabTokenSigner,
) {

    private val log = LoggerFactory.getLogger(LabLoginController::class.java)

    data class LabLoginRequest(val email: String, val password: String)

    @PostMapping
    fun login(@RequestBody request: LabLoginRequest): Map<String, String> {
        // VULNERABILIDAD INTENCIONAL (práctica SAST #4): Información sensible en logs (CWE-532)
        log.info("Intento de login: email={} password={}", request.email, request.password)
        val hash = hasher.hashear(request.password)
        return mapOf("token" to signer.firmar("${request.email}:$hash"))
    }
}
