package com.authrisk.domain.port

import com.authrisk.domain.totp.LoginChallenge
import com.authrisk.domain.totp.UserTotp
import java.time.Instant
import java.util.UUID

/** Persistencia de la configuración TOTP. Las operaciones condicionales son atómicas. */
interface UserTotpRepository {
    fun findByUserId(userId: UUID): UserTotp?

    /** Crea o reemplaza el secreto pendiente. Devuelve false (sin cambios) si el 2FA ya está activo. */
    fun savePending(userId: UUID, encryptedSecret: String, now: Instant): Boolean

    /** Marca [step] como usado solo si es posterior al último usado. Devuelve false si no lo es. */
    fun markStepUsed(userId: UUID, step: Long): Boolean

    /** Activa el 2FA si aún no lo estaba. */
    fun enable(userId: UUID, now: Instant): Boolean
}

/** Cifra el secreto TOTP ligándolo al usuario (el cifrado de un usuario no sirve para otro). */
interface TotpSecretCipher {
    fun encrypt(userId: UUID, secret: ByteArray): String
    fun decrypt(userId: UUID, encrypted: String): ByteArray
}

/** Desafíos de login pendientes. */
interface LoginChallengeRepository {
    fun save(challenge: LoginChallenge)

    /** Lee el desafío bloqueando la fila hasta el final de la transacción. */
    fun findByHashForUpdate(idHash: String): LoginChallenge?

    fun update(challenge: LoginChallenge)

    /** Borra los desafíos vencidos o cerrados. Devuelve cuántos borró. */
    fun deleteClosed(now: Instant): Int
}

/** Identificadores opacos de alta entropía y el hash con el que se almacenan. */
interface ChallengeIdGenerator {
    fun generate(): String
    fun hash(rawId: String): String
}
