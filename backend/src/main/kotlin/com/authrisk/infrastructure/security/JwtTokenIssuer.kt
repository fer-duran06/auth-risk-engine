package com.authrisk.infrastructure.security

import com.authrisk.domain.port.TokenIssuer
import com.authrisk.domain.token.AccessToken
import com.authrisk.domain.user.User
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

/** Emite JWT de acceso HS256: `sub` = id de usuario, `role` = rol. Sin datos sensibles en los claims. */
@Component
class JwtTokenIssuer(
    private val encoder: JwtEncoder,
    private val props: AuthProperties,
    private val clock: Clock,
) : TokenIssuer {

    override fun issueAccessToken(user: User): AccessToken {
        val now = clock.instant()
        val expiresAt = now.plus(props.accessTokenTtl)
        val claims = JwtClaimsSet.builder()
            .issuer(props.jwtIssuer)
            .subject(user.id.toString())
            .id(UUID.randomUUID().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim(ROLE_CLAIM, user.role.name)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val jwt = encoder.encode(JwtEncoderParameters.from(header, claims))
        return AccessToken(jwt.tokenValue, expiresAt)
    }

    companion object {
        const val ROLE_CLAIM = "role"
    }
}
