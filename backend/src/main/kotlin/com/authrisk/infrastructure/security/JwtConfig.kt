package com.authrisk.infrastructure.security

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/** Codificador y decodificador JWT HS256 con la clave simétrica de JWT_SECRET. */
@Configuration
class JwtConfig(private val props: AuthProperties) {

    @Bean
    fun jwtSigningKey(): SecretKey {
        val bytes = props.jwtSecret.toByteArray(Charsets.UTF_8)
        // HS256 requiere al menos 256 bits; se falla al arrancar en vez de firmar con una clave débil.
        require(bytes.size >= MIN_SECRET_BYTES) {
            "JWT_SECRET no está definido o tiene menos de $MIN_SECRET_BYTES bytes"
        }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(jwtSigningKey: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(jwtSigningKey))

    @Bean
    fun jwtDecoder(jwtSigningKey: SecretKey): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build().apply {
            // Valida exp/nbf (con tolerancia por defecto) y el emisor.
            setJwtValidator(JwtValidators.createDefaultWithIssuer(props.jwtIssuer))
        }

    companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
