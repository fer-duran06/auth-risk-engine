package com.authrisk.application.twofactor

import com.authrisk.application.port.TransactionRunner
import com.authrisk.domain.port.TotpSecretCipher
import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.port.UserTotpRepository
import com.authrisk.domain.totp.Base32
import com.authrisk.domain.totp.InvalidTotpCodeException
import com.authrisk.domain.totp.Totp
import com.authrisk.domain.totp.TotpAlreadyEnabledException
import com.authrisk.domain.user.UserNotFoundException
import java.net.URLEncoder
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Datos para registrar la cuenta en la app autenticadora. Se muestran una sola vez. */
data class TotpSetup(val secret: String, val otpauthUri: String) {
    override fun toString(): String = "TotpSetup(***)"
}

data class TotpStatus(val enabled: Boolean, val confirmedAt: Instant?)

/**
 * Genera (o reemplaza, si aún no se confirmó) el secreto TOTP del usuario.
 * El secreto se guarda cifrado y solo se devuelve en claro en esta respuesta.
 */
class SetupTotpUseCase(
    private val users: UserRepository,
    private val totpRepo: UserTotpRepository,
    private val cipher: TotpSecretCipher,
    private val totp: Totp,
    private val tx: TransactionRunner,
    private val clock: Clock,
    private val issuer: String,
) {
    fun execute(userId: UUID): TotpSetup {
        val user = users.findById(userId) ?: throw UserNotFoundException()
        val secret = Totp.newSecret()
        val saved = tx.inTransaction { totpRepo.savePending(userId, cipher.encrypt(userId, secret), clock.instant()) }
        if (!saved) throw TotpAlreadyEnabledException()
        val base32 = Base32.encode(secret)
        return TotpSetup(base32, otpauthUri(user.email.value, base32))
    }

    /** Formato Key Uri de Google Authenticator: otpauth://totp/Emisor:cuenta?secret=…&issuer=… */
    private fun otpauthUri(account: String, secret: String): String {
        fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")
        val label = "${enc(issuer)}:${enc(account)}"
        return "otpauth://totp/$label?secret=$secret&issuer=${enc(issuer)}" +
            "&algorithm=SHA1&digits=${totp.digits}&period=${totp.periodSeconds}"
    }
}

/** Confirma la configuración con un primer código válido y activa el 2FA. */
class EnableTotpUseCase(
    private val totpRepo: UserTotpRepository,
    private val cipher: TotpSecretCipher,
    private val totp: Totp,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    fun execute(userId: UUID, code: String) {
        val now = clock.instant()
        val enabled = tx.inTransaction {
            val config = totpRepo.findByUserId(userId) ?: return@inTransaction false
            if (config.enabled) throw TotpAlreadyEnabledException()
            val step = totp.verify(cipher.decrypt(userId, config.encryptedSecret), code, now)
                ?: return@inTransaction false
            // El código de la activación tampoco puede volver a usarse en un login.
            totpRepo.markStepUsed(userId, step) && totpRepo.enable(userId, now)
        }
        if (!enabled) throw InvalidTotpCodeException()
    }
}

class GetTotpStatusUseCase(private val totpRepo: UserTotpRepository) {
    fun execute(userId: UUID): TotpStatus {
        val config = totpRepo.findByUserId(userId)
        return if (config?.enabled == true) TotpStatus(true, config.confirmedAt) else TotpStatus(false, null)
    }
}
