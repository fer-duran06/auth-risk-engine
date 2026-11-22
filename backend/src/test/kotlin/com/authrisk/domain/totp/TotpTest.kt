package com.authrisk.domain.totp

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TotpTest {
    // Semilla SHA-1 del RFC 6238, Apéndice B: "12345678901234567890" en ASCII.
    private val rfcSeed = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    /** Vectores SHA-1 del RFC 6238, Apéndice B (8 dígitos). */
    private val rfcVectors = listOf(
        59L to "94287082",
        1111111109L to "07081804",
        1111111111L to "14050471",
        1234567890L to "89005924",
        2000000000L to "69279037",
        20000000000L to "65353130",
    )

    @Test
    fun `reproduce los vectores de prueba del RFC 6238 con 8 dígitos`() {
        val totp = Totp(digits = 8)
        rfcVectors.forEach { (time, expected) ->
            assertEquals(expected, totp.generate(rfcSeed, totp.stepAt(Instant.ofEpochSecond(time))), "T=$time")
        }
    }

    @Test
    fun `con 6 dígitos el código son los últimos 6 del vector`() {
        val totp = Totp()
        rfcVectors.forEach { (time, expected) ->
            assertEquals(expected.takeLast(6), totp.generate(rfcSeed, totp.stepAt(Instant.ofEpochSecond(time))))
        }
    }

    @Test
    fun `el paso es floor del tiempo unix entre 30`() {
        val totp = Totp()
        assertEquals(1L, totp.stepAt(Instant.ofEpochSecond(59)))
        assertEquals(0x23523EC, totp.stepAt(Instant.ofEpochSecond(1111111109)))
        assertEquals(2L, totp.stepAt(Instant.ofEpochSecond(60)))
    }

    @Test
    fun `verify acepta el paso actual y uno de tolerancia en cada sentido`() {
        val totp = Totp()
        val now = Instant.ofEpochSecond(1234567890)
        val step = totp.stepAt(now)
        assertEquals(step, totp.verify(rfcSeed, totp.generate(rfcSeed, step), now))
        assertEquals(step - 1, totp.verify(rfcSeed, totp.generate(rfcSeed, step - 1), now))
        assertEquals(step + 1, totp.verify(rfcSeed, totp.generate(rfcSeed, step + 1), now))
        assertNull(totp.verify(rfcSeed, totp.generate(rfcSeed, step - 2), now))
        assertNull(totp.verify(rfcSeed, totp.generate(rfcSeed, step + 2), now))
    }

    @Test
    fun `verify rechaza formatos inválidos y secretos distintos`() {
        val totp = Totp()
        val now = Instant.ofEpochSecond(59)
        val code = totp.generate(rfcSeed, totp.stepAt(now))
        listOf("", "12345", "1234567", "12a456", " $code", "١٢٣٤٥٦").forEach { assertNull(totp.verify(rfcSeed, it, now), it) }
        assertNull(totp.verify("otra-semilla-distinta".toByteArray(), code, now))
    }

    @Test
    fun `newSecret genera 160 bits distintos cada vez`() {
        val a = Totp.newSecret()
        assertEquals(20, a.size)
        assert(!a.contentEquals(Totp.newSecret()))
    }

    @Test
    fun `parámetros fuera de rango se rechazan`() {
        assertFailsWith<IllegalArgumentException> { Totp(digits = 5) }
        assertFailsWith<IllegalArgumentException> { Totp(periodSeconds = 0) }
        assertFailsWith<IllegalArgumentException> { Totp(window = 3) }
    }

    @Test
    fun `Base32 coincide con los vectores del RFC 4648 y es reversible`() {
        val vectors = mapOf("" to "", "f" to "MY", "fo" to "MZXQ", "foo" to "MZXW6", "foob" to "MZXW6YQ", "fooba" to "MZXW6YTB", "foobar" to "MZXW6YTBOI")
        vectors.forEach { (plain, encoded) ->
            assertEquals(encoded, Base32.encode(plain.toByteArray()))
            assertContentEquals(plain.toByteArray(), Base32.decode(encoded))
        }
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Base32.encode(rfcSeed))
        assertContentEquals(rfcSeed, Base32.decode("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"))
        assertFailsWith<IllegalArgumentException> { Base32.decode("MZXW1") }
    }
}
