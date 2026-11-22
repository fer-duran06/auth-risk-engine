package com.authrisk.infrastructure.web.auth

import com.authrisk.application.twofactor.EnableTotpUseCase
import com.authrisk.application.twofactor.GetTotpStatusUseCase
import com.authrisk.application.twofactor.SetupTotpUseCase
import com.authrisk.application.twofactor.VerifyLoginChallengeUseCase
import com.authrisk.infrastructure.web.ClientIpResolver
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Autenticación en dos pasos")
@RestController
@RequestMapping("/api/auth/2fa")
class TwoFactorController(
    private val setup: SetupTotpUseCase,
    private val enable: EnableTotpUseCase,
    private val status: GetTotpStatusUseCase,
    private val verify: VerifyLoginChallengeUseCase,
    private val sessions: SessionResponses,
    private val clientIp: ClientIpResolver,
) {

    @Operation(
        summary = "Completa un desafío CHALLENGE con un código TOTP. Cualquier fallo da el mismo 401 genérico.",
    )
    @PostMapping("/verify")
    fun verify(@Valid @RequestBody body: VerifyChallengeRequest, request: HttpServletRequest): ResponseEntity<TokenResponse> =
        sessions.tokens(verify.execute(body.challengeId, body.code, clientIp.resolve(request)))

    @Operation(
        summary = "Genera un secreto TOTP pendiente y devuelve la clave y el URI otpauth.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @PostMapping("/setup")
    fun setup(@AuthenticationPrincipal jwt: Jwt): TotpSetupResponse =
        TotpSetupResponse.from(setup.execute(UUID.fromString(jwt.subject)))

    @Operation(
        summary = "Confirma el secreto pendiente con un código y activa el 2FA.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @PostMapping("/enable")
    fun enable(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody body: TotpCodeRequest): ResponseEntity<Void> {
        enable.execute(UUID.fromString(jwt.subject), body.code)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Estado del 2FA del usuario autenticado.", security = [SecurityRequirement(name = "bearerAuth")])
    @GetMapping("/status")
    fun status(@AuthenticationPrincipal jwt: Jwt): TotpStatusResponse =
        TotpStatusResponse.from(status.execute(UUID.fromString(jwt.subject)))
}
