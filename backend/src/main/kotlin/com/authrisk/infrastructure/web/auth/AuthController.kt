package com.authrisk.infrastructure.web.auth

import com.authrisk.application.auth.AuthTokens
import com.authrisk.application.auth.GetCurrentUserUseCase
import com.authrisk.application.auth.LoginUseCase
import com.authrisk.application.auth.LogoutUseCase
import com.authrisk.application.auth.RefreshUseCase
import com.authrisk.application.auth.RegisterUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Duration
import java.util.UUID

@Tag(name = "Autenticación")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val register: RegisterUseCase,
    private val login: LoginUseCase,
    private val refresh: RefreshUseCase,
    private val logout: LogoutUseCase,
    private val currentUser: GetCurrentUserUseCase,
    private val cookies: RefreshCookieFactory,
    private val clock: Clock,
) {

    @Operation(summary = "Registra un usuario. Respuesta idéntica exista o no el email.")
    @PostMapping("/register")
    fun register(@Valid @RequestBody body: RegisterRequest): ResponseEntity<MessageResponse> {
        register.execute(body.email, body.password)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(MessageResponse(REGISTER_MESSAGE))
    }

    @Operation(summary = "Inicia sesión: devuelve el access token y fija la cookie de refresh.")
    @PostMapping("/login")
    fun login(@Valid @RequestBody body: LoginRequest): ResponseEntity<TokenResponse> =
        tokensResponse(login.execute(body.email, body.password))

    @Operation(summary = "Rota el refresh token de la cookie y emite un access token nuevo.")
    @PostMapping("/refresh")
    fun refresh(@CookieValue(RefreshCookieFactory.NAME, required = false) refreshToken: String?): ResponseEntity<TokenResponse> =
        tokensResponse(refresh.execute(refreshToken))

    @Operation(summary = "Cierra la sesión: revoca la familia del refresh token y borra la cookie.")
    @PostMapping("/logout")
    fun logout(@CookieValue(RefreshCookieFactory.NAME, required = false) refreshToken: String?): ResponseEntity<Void> {
        logout.execute(refreshToken)
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build()
    }

    @Operation(summary = "Datos del usuario autenticado.", security = [SecurityRequirement(name = "bearerAuth")])
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): UserResponse =
        UserResponse.from(currentUser.execute(UUID.fromString(jwt.subject)))

    private fun tokensResponse(tokens: AuthTokens): ResponseEntity<TokenResponse> {
        val expiresIn = Duration.between(clock.instant(), tokens.accessToken.expiresAt).seconds.coerceAtLeast(0)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.create(tokens.refreshToken, tokens.refreshTokenExpiresAt).toString())
            .body(TokenResponse(accessToken = tokens.accessToken.value, expiresIn = expiresIn))
    }

    companion object {
        const val REGISTER_MESSAGE = "Si los datos son válidos, la cuenta quedó registrada. Ya puedes iniciar sesión."
    }
}
