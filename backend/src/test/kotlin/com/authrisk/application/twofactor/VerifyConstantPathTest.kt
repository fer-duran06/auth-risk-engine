package com.authrisk.application.twofactor

import com.authrisk.application.auth.AuthFixture
import com.authrisk.application.auth.AuthFixture.Companion.PASSWORD
import com.authrisk.application.auth.IP
import com.authrisk.domain.port.LoginChallengeRepository
import com.authrisk.domain.port.TotpSecretCipher
import com.authrisk.domain.port.UserTotpRepository
import com.authrisk.domain.risk.ChallengeRequiredException
import com.authrisk.domain.risk.RiskSignals
import com.authrisk.domain.totp.LoginChallenge
import com.authrisk.domain.totp.Totp
import com.authrisk.domain.totp.UserTotp
import com.authrisk.domain.user.InvalidCredentialsException
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Canal de tiempo de /2fa/verify: todo fallo debe ejecutar la misma secuencia de operaciones
 * (mismas lecturas, un descifrado y los mismos UPDATE). Se registra cada llamada a los puertos
 * y si usó el usuario real o el ficticio.
 */
class VerifyConstantPathTest {
    private val f = AuthFixture()
    private val trace = mutableListOf<String>()
    private lateinit var userId: UUID
    private val secret = Totp.newSecret()

    private fun who(id: UUID) = if (id == DUMMY_USER_ID) "ficticio" else "real"

    private val challenges = object : LoginChallengeRepository by f.challenges {
        override fun findByHashForUpdate(idHash: String): LoginChallenge? {
            trace += "leer desafío"
            return f.challenges.findByHashForUpdate(idHash)
        }

        override fun updateAttempts(idHash: String, attempts: Int, consumedAt: Instant?): Int {
            trace += "UPDATE desafío"
            return f.challenges.updateAttempts(idHash, attempts, consumedAt)
        }
    }

    private val totpRepo = object : UserTotpRepository by f.totpRepo {
        override fun findByUserId(userId: UUID): UserTotp? {
            trace += "leer 2FA ${who(userId)}"
            return f.totpRepo.findByUserId(userId)
        }

        override fun markStepUsed(userId: UUID, step: Long): Boolean {
            trace += "UPDATE paso ${who(userId)}"
            return f.totpRepo.markStepUsed(userId, step)
        }
    }

    private val cipher = object : TotpSecretCipher by f.cipher {
        override fun decrypt(userId: UUID, encrypted: String): ByteArray {
            trace += "descifrar ${who(userId)}"
            return f.cipher.decrypt(userId, encrypted)
        }
    }

    private val verify = VerifyLoginChallengeUseCase(
        challenges, f.challengeIds, totpRepo, cipher, f.totp, f.users, f.refreshTokens,
        f.sessions, f.loginAttempts, f.tx, f.clock, f.maxVerifyAttempts,
    )

    @BeforeTest
    fun setUp() {
        f.register.execute("ana@example.com", PASSWORD)
        f.register.execute("sin2fa@example.com", PASSWORD)
        userId = f.users.findByEmail(com.authrisk.domain.user.Email.of("ana@example.com"))!!.id
        f.totpRepo.savePending(userId, f.cipher.encrypt(userId, secret), f.clock.instant())
        f.totpRepo.enable(userId, f.clock.instant())
        f.riskCounters.signals = RiskSignals(ipAttempts = 10) // CHALLENGE
    }

    private fun code(offset: Long = 0) = f.totp.generate(secret, f.totp.stepAt(f.clock.instant()) + offset)
    private fun wrongCode() = if (code() == "000000") "111111" else "000000"

    private fun challenge(email: String = "ana@example.com", pwd: String = PASSWORD): String =
        assertFailsWith<ChallengeRequiredException> { f.login.execute(email, pwd, IP) }.challengeId

    /** Ejecuta un verify fallido y devuelve sus operaciones (sin real/ficticio) y el detalle completo. */
    private fun failing(challengeId: String, code: String): Pair<List<String>, List<String>> {
        trace.clear()
        val ex = assertFailsWith<InvalidCredentialsException> { verify.execute(challengeId, code, IP) }
        assertEquals("Credenciales inválidas", ex.message)
        return trace.map { it.removeSuffix(" real").removeSuffix(" ficticio") } to trace.toList()
    }

    private val sameWork = listOf("leer desafío", "leer 2FA", "descifrar", "UPDATE paso", "UPDATE desafío")

    @Test
    fun `todos los motivos de fallo ejecutan la misma secuencia de operaciones`() {
        val cases = linkedMapOf<String, Pair<List<String>, List<String>>>()
        cases["contraseña incorrecta"] = failing(challenge(pwd = "incorrecta-123"), code())
        cases["email inexistente"] = failing(challenge(email = "nadie@example.com"), code())
        cases["usuario sin 2FA"] = failing(challenge(email = "sin2fa@example.com"), code())
        cases["código inválido"] = failing(challenge(), wrongCode())
        cases["challengeId desconocido"] = failing("no-existe", code())
        val expired = challenge()
        f.clock.advance(f.challengeTtl)
        cases["desafío vencido"] = failing(expired, code())
        val exhausted = challenge()
        repeat(f.maxVerifyAttempts) { failing(exhausted, wrongCode()) }
        cases["desafío agotado"] = failing(exhausted, code())

        cases.forEach { (name, ops) -> assertEquals(sameWork, ops.first, name) }
    }

    @Test
    fun `el camino ficticio se usa cuando el código no puede aceptarse`() {
        // Sin candidato (contraseña incorrecta o desafío cerrado): todo ficticio.
        assertEquals(
            listOf("leer desafío", "leer 2FA ficticio", "descifrar ficticio", "UPDATE paso ficticio", "UPDATE desafío"),
            failing(challenge(pwd = "incorrecta-123"), code()).second,
        )
        // Contraseña correcta sin 2FA: se lee la fila real, pero se descifra el secreto ficticio.
        assertEquals(
            listOf("leer desafío", "leer 2FA real", "descifrar ficticio", "UPDATE paso ficticio", "UPDATE desafío"),
            failing(challenge(email = "sin2fa@example.com"), code()).second,
        )
        // 2FA activo y código inválido: secreto real, pero el UPDATE del paso es el ficticio (0 filas).
        assertEquals(
            listOf("leer desafío", "leer 2FA real", "descifrar real", "UPDATE paso ficticio", "UPDATE desafío"),
            failing(challenge(), wrongCode()).second,
        )
    }

    @Test
    fun `el éxito usa los datos reales y un código reutilizado no pasa`() {
        trace.clear()
        verify.execute(challenge(), code(), IP)
        assertEquals(
            listOf("leer desafío", "leer 2FA real", "descifrar real", "UPDATE paso real", "UPDATE desafío"),
            trace,
        )
        // Reutilización: el UPDATE real no afecta filas y el resultado es el mismo 401.
        val (ops, detail) = failing(challenge(), code())
        assertEquals(sameWork, ops)
        assertEquals("UPDATE paso real", detail[3])
    }

    @Test
    fun `si el descifrado falla el código no se acepta y la respuesta es el mismo 401`() {
        // Secreto corrupto (como si la clave hubiera cambiado): el descifrado lanza.
        f.totpRepo.rows[userId] = f.totpRepo.rows.getValue(userId).copy(encryptedSecret = "$userId:%%no-es-base64%%")

        val (ops, detail) = failing(challenge(), code())

        assertEquals(sameWork, ops)
        assertEquals("UPDATE paso ficticio", detail[3])
        assertEquals(0, f.refreshTokens.tokens.size)
    }
}
