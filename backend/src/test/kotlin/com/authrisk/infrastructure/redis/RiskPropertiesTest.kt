package com.authrisk.infrastructure.redis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RiskPropertiesTest {

    @Test
    fun `el motor está activo por defecto, con o sin modo demo`() {
        assertEquals(true, RiskProperties().effectiveEnabled(demoMode = false))
        assertEquals(true, RiskProperties().effectiveEnabled(demoMode = true))
    }

    @Test
    fun `desactivarlo solo se permite en modo demo`() {
        assertEquals(false, RiskProperties(enabled = false).effectiveEnabled(demoMode = true))
        val ex = assertFailsWith<IllegalStateException> { RiskProperties(enabled = false).effectiveEnabled(demoMode = false) }
        assertEquals("app.risk.enabled=false solo se permite con app.demo-mode=true", ex.message)
    }
}
