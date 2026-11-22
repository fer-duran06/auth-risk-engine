package com.authrisk.domain.totp

import com.authrisk.domain.risk.RiskAssessment
import java.time.Instant
import java.util.UUID

/**
 * Configuración TOTP de un usuario. [encryptedSecret] es el secreto cifrado (AES-GCM);
 * el secreto en claro nunca se persiste. [lastUsedStep] impide reutilizar un código.
 */
data class UserTotp(
    val userId: UUID,
    val encryptedSecret: String,
    val enabled: Boolean,
    val confirmedAt: Instant?,
    val lastUsedStep: Long?,
    val createdAt: Instant,
) {
    override fun toString(): String = "UserTotp(userId=$userId, enabled=$enabled, confirmedAt=$confirmedAt)"
}

/**
 * Desafío emitido por un login en zona CHALLENGE. Se crea igual sea la contraseña correcta o no;
 * [passwordOk] solo lo conoce el servidor. Del identificador solo se guarda el hash ([idHash]).
 * [consumedAt] marca un desafío usado con éxito o invalidado por agotar los intentos.
 */
data class LoginChallenge(
    val id: UUID,
    val idHash: String,
    val userId: UUID?,
    val passwordOk: Boolean,
    val email: String,
    val ip: String,
    val assessment: RiskAssessment,
    val attempts: Int,
    val expiresAt: Instant,
    val consumedAt: Instant?,
    val createdAt: Instant,
) {
    init {
        require(!passwordOk || userId != null) { "Un desafío con contraseña correcta necesita usuario" }
    }

    fun isOpen(now: Instant, maxAttempts: Int): Boolean =
        consumedAt == null && now.isBefore(expiresAt) && attempts < maxAttempts

    override fun toString(): String = "LoginChallenge(id=$id, attempts=$attempts, expiresAt=$expiresAt)"
}

/** El 2FA ya está activo: no se puede volver a configurar desde este flujo. */
class TotpAlreadyEnabledException : RuntimeException("El 2FA ya está activo")

/** Código TOTP inválido, reutilizado o sin configuración pendiente. Mensaje único. */
class InvalidTotpCodeException : RuntimeException("Código inválido")
