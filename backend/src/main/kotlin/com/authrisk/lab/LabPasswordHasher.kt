package com.authrisk.lab

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.security.MessageDigest

/** Código de laboratorio para la práctica SAST. Solo se activa con el perfil "lab". */
@Profile("lab")
@Component
class LabPasswordHasher {

    fun hashear(password: String): String {
        // VULNERABILIDAD INTENCIONAL (práctica SAST #3): Hash débil MD5 para contraseñas (CWE-327)
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(password.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
