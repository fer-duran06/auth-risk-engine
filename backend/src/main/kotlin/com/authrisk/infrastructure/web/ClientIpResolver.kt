package com.authrisk.infrastructure.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.InetAddress

/**
 * IP del cliente para el motor de riesgo: la dirección de la conexión (`request.remoteAddr`).
 *
 * Detrás de un proxy de confianza, `remoteAddr` ya no es necesariamente la IP real: por eso
 * `server.forward-headers-strategy=native` (ver application.yml) activa el RemoteIpValve de
 * Tomcat, que reescribe `remoteAddr` a partir de `X-Forwarded-For` únicamente cuando la conexión
 * TCP entrante proviene de una IP de la lista de proxies internos de confianza (por defecto,
 * rangos privados). Fuera de esos rangos el header se ignora y `remoteAddr` sigue siendo la
 * dirección real del socket, así que un cliente arbitrario no puede falsificar su IP con ese header.
 * Solo con `app.demo-mode=true` se acepta `X-Simulated-IP` (con prioridad sobre lo anterior), para
 * que el simulador de ataques pueda variar la IP de origen.
 */
@Component
class ClientIpResolver(@Value("\${app.demo-mode:false}") private val demoMode: Boolean) {

    fun resolve(request: HttpServletRequest): String {
        if (demoMode) {
            parseIpLiteral(request.getHeader(SIMULATED_IP_HEADER))?.let { return it }
        }
        return parseIpLiteral(request.remoteAddr) ?: UNKNOWN
    }

    companion object {
        const val SIMULATED_IP_HEADER = "X-Simulated-IP"
        const val UNKNOWN = "unknown"

        private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$""")
        // Solo caracteres de un literal IPv6: con ':' obligatorio, InetAddress no hace consultas DNS.
        private val IPV6_CHARS = Regex("""^[0-9a-fA-F:.]{2,45}$""")

        /** Devuelve la IP normalizada, o null si no es un literal IPv4/IPv6 válido. */
        fun parseIpLiteral(raw: String?): String? {
            val value = raw?.trim() ?: return null
            if (IPV4.matches(value)) return value
            if (!value.contains(':') || !IPV6_CHARS.matches(value)) return null
            return runCatching { InetAddress.getByName(value).hostAddress }.getOrNull()
        }
    }
}
