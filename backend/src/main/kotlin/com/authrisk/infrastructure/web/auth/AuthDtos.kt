package com.authrisk.infrastructure.web.auth

import com.authrisk.application.twofactor.TotpSetup
import com.authrisk.application.twofactor.TotpStatus
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.PasswordPolicy
import com.authrisk.domain.user.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

// Los DTOs con secretos redefinen toString() para que nunca aparezcan en logs.

data class RegisterRequest(
    @field:NotBlank @field:Size(max = Email.MAX_LENGTH)
    val email: String,
    @field:NotBlank @field:Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH)
    val password: String,
) {
    override fun toString(): String = "RegisterRequest(email=$email)"
}

data class LoginRequest(
    @field:NotBlank @field:Size(max = Email.MAX_LENGTH)
    val email: String,
    // Solo el máximo: en login no se informa la política, solo se acota el costo de Argon2.
    @field:NotBlank @field:Size(max = PasswordPolicy.MAX_LENGTH)
    val password: String,
) {
    override fun toString(): String = "LoginRequest(email=$email)"
}

data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    /** Segundos hasta que expira el access token. */
    val expiresIn: Long,
) {
    override fun toString(): String = "TokenResponse(tokenType=$tokenType, expiresIn=$expiresIn)"
}

data class UserResponse(val id: UUID, val email: String, val role: String, val createdAt: Instant) {
    companion object {
        fun from(user: User) = UserResponse(user.id, user.email.value, user.role.name, user.createdAt)
    }
}

data class MessageResponse(val message: String)

data class ErrorResponse(val error: String, val details: List<String> = emptyList())

/**
 * 403 de la zona CHALLENGE: código estable para que el cliente pida el TOTP y el challengeId
 * opaco de un solo uso con el que se verifica.
 */
data class ChallengeResponse(val error: String, val code: String, val challengeId: String) {
    override fun toString(): String = "ChallengeResponse(code=$code)"
}

data class VerifyChallengeRequest(
    @field:NotBlank @field:Size(max = 128)
    val challengeId: String,
    @field:NotBlank @field:Size(max = 16)
    val code: String,
) {
    override fun toString(): String = "VerifyChallengeRequest(***)"
}

data class TotpCodeRequest(
    @field:NotBlank @field:Size(max = 16)
    val code: String,
) {
    override fun toString(): String = "TotpCodeRequest(***)"
}

/** El secreto solo se devuelve aquí, una vez, para registrarlo en la app autenticadora. */
data class TotpSetupResponse(val secret: String, val otpauthUri: String) {
    override fun toString(): String = "TotpSetupResponse(***)"

    companion object {
        fun from(setup: TotpSetup) = TotpSetupResponse(setup.secret, setup.otpauthUri)
    }
}

data class TotpStatusResponse(val enabled: Boolean, val confirmedAt: Instant?) {
    companion object {
        fun from(status: TotpStatus) = TotpStatusResponse(status.enabled, status.confirmedAt)
    }
}
