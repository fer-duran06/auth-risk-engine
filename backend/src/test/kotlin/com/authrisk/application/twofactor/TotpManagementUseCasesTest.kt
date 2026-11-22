package com.authrisk.application.twofactor

import com.authrisk.application.auth.AuthFixture
import com.authrisk.application.auth.FakeTotpSecretCipher
import com.authrisk.application.auth.InMemoryUserTotpRepository
import com.authrisk.domain.totp.Base32
import com.authrisk.domain.totp.InvalidTotpCodeException
import com.authrisk.domain.totp.Totp
import com.authrisk.domain.totp.TotpAlreadyEnabledException
import com.authrisk.domain.user.UserNotFoundException
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TotpManagementUseCasesTest {
    private val f = AuthFixture()
    private val totpRepo = InMemoryUserTotpRepository()
    private val cipher = FakeTotpSecretCipher()
    private val totp = Totp()
    private val setup = SetupTotpUseCase(f.users, totpRepo, cipher, totp, f.tx, f.clock, "AuthShield")
    private val enable = EnableTotpUseCase(totpRepo, cipher, totp, f.tx, f.clock)
    private val status = GetTotpStatusUseCase(totpRepo)

    private val userId: UUID = run {
        f.register.execute("ana@example.com", AuthFixture.PASSWORD)
        f.users.users.keys.single()
    }

    private fun codeFor(secret: String, offsetSteps: Long = 0) =
        totp.generate(Base32.decode(secret), totp.stepAt(f.clock.instant()) + offsetSteps)

    @Test
    fun `setup guarda el secreto cifrado y devuelve un URI otpauth`() {
        val result = setup.execute(userId)

        assertEquals(32, result.secret.length) // 20 bytes en Base32
        assertEquals(
            "otpauth://totp/AuthShield:ana%40example.com?secret=${result.secret}&issuer=AuthShield&algorithm=SHA1&digits=6&period=30",
            result.otpauthUri,
        )
        val row = totpRepo.rows.getValue(userId)
        assertFalse(row.enabled)
        assertFalse(row.encryptedSecret.contains(result.secret))
        assertFalse(status.execute(userId).enabled)
        assertFalse(result.toString().contains(result.secret))
    }

    @Test
    fun `repetir setup antes de confirmar reemplaza el secreto`() {
        val first = setup.execute(userId)
        val second = setup.execute(userId)
        assertNotEquals(first.secret, second.secret)
        assertFailsWith<InvalidTotpCodeException> { enable.execute(userId, codeFor(first.secret)) }
        enable.execute(userId, codeFor(second.secret))
    }

    @Test
    fun `enable con un código válido activa el 2FA y marca el paso como usado`() {
        val secret = setup.execute(userId).secret
        enable.execute(userId, codeFor(secret, offsetSteps = -1))

        val st = status.execute(userId)
        assertTrue(st.enabled)
        assertEquals(f.clock.instant(), st.confirmedAt)
        assertEquals(totp.stepAt(f.clock.instant()) - 1, totpRepo.rows.getValue(userId).lastUsedStep)
        // Ya activo: ni setup ni enable pueden reconfigurarlo.
        assertFailsWith<TotpAlreadyEnabledException> { setup.execute(userId) }
        assertFailsWith<TotpAlreadyEnabledException> { enable.execute(userId, codeFor(secret)) }
    }

    @Test
    fun `enable rechaza códigos incorrectos, fuera de ventana o sin setup previo`() {
        assertFailsWith<InvalidTotpCodeException> { enable.execute(userId, "123456") }
        val secret = setup.execute(userId).secret
        assertFailsWith<InvalidTotpCodeException> { enable.execute(userId, codeFor(secret, offsetSteps = 2)) }
        assertFailsWith<InvalidTotpCodeException> { enable.execute(userId, "abcdef") }
        f.clock.advance(Duration.ofSeconds(90))
        assertFailsWith<InvalidTotpCodeException> { enable.execute(userId, codeFor(secret, offsetSteps = -3)) }
        assertFalse(status.execute(userId).enabled)
        assertNull(status.execute(userId).confirmedAt)
    }

    @Test
    fun `setup de un usuario inexistente falla`() {
        assertFailsWith<UserNotFoundException> { setup.execute(UUID.randomUUID()) }
    }
}
