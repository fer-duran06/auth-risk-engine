package com.authrisk.lab

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Código de laboratorio para la práctica SAST. Solo se activa con el perfil "lab". */
@Profile("lab")
@Component
class LabTokenSigner {

    companion object {
        // VULNERABILIDAD INTENCIONAL (práctica SAST #2): Secreto hardcodeado (CWE-798)
        private const val JWT_SECRET = "authshield-lab-jwt-secret-no-usar-2026"
    }

    /** Firma un payload con HMAC-SHA256 y devuelve la firma en Base64 URL. */
    fun firmar(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(JWT_SECRET.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray()))
    }
}
