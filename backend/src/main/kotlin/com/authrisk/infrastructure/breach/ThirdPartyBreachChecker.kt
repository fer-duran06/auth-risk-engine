package com.authrisk.infrastructure.breach

import com.authrisk.domain.port.BreachCheckPort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat

/**
 * Adaptador a dos APIs públicas gratuitas (sin clave): Pwned Passwords de HIBP para contraseñas
 * filtradas, y XposedOrNot para correos en brechas conocidas. Ambas se consultan con timeouts
 * cortos; cualquier excepción se registra (solo el tipo) y se traduce en `null`, nunca se propaga.
 */
@Component
class ThirdPartyBreachChecker(private val objectMapper: ObjectMapper) : BreachCheckPort {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

    override fun isPasswordPwned(rawPassword: String): Boolean? {
        val sha1 = sha1Hex(rawPassword)
        val prefix = sha1.substring(0, 5)
        val suffix = sha1.substring(5)
        return runCatching {
            val request = HttpRequest.newBuilder(URI.create("$PWNED_PASSWORDS_URL/$prefix"))
                .timeout(Duration.ofSeconds(3))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                null
            } else {
                // Cada línea es "SUFIJO:CONTADOR"; basta con que el sufijo del hash completo aparezca.
                response.body().lineSequence().any { it.startsWith(suffix, ignoreCase = true) }
            }
        }.onFailure { warn("Pwned Passwords", it) }.getOrNull()
    }

    override fun breachesForEmail(email: String): Int? = runCatching {
        val request = HttpRequest.newBuilder(URI.create("$XPOSED_OR_NOT_URL/${encode(email)}"))
            .timeout(Duration.ofSeconds(3))
            .header("User-Agent", USER_AGENT)
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        when (response.statusCode()) {
            // Sin coincidencias: la propia API usa 404 para "no encontrado".
            404 -> 0
            200 -> objectMapper.readTree(response.body()).get("breaches")?.firstOrNull()?.size() ?: 0
            else -> null
        }
    }.onFailure { warn("XposedOrNot", it) }.getOrNull()

    private fun warn(service: String, ex: Throwable) =
        log.warn("$service no disponible ({}): se omite la verificación", ex.javaClass.name)

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun sha1Hex(value: String): String =
        HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1").digest(value.toByteArray()))

    private companion object {
        const val PWNED_PASSWORDS_URL = "https://api.pwnedpasswords.com/range"
        const val XPOSED_OR_NOT_URL = "https://api.xposedornot.com/v1/check-email"
        const val USER_AGENT = "AuthShield-UPChiapas-proyecto-academico"
    }
}
