package com.hourglass.core.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {

    @Test
    fun `greying drains colour but keeps light and dark apart`() {
        val amber = 0xFFE0A63C.toInt()
        assertEquals(amber, Palettes.greyed(amber, 0f))
        val grey = Palettes.greyed(amber, 1f)
        val r = (grey ushr 16) and 0xFF
        val g = (grey ushr 8) and 0xFF
        val b = grey and 0xFF
        assertTrue("fully greyed should be neutral", r == g && g == b)
        assertEquals(0xFF, grey ushr 24)
        val dark = Palettes.greyed(0xFF2E2A2C.toInt(), 1f) and 0xFF
        assertTrue("light stays lighter than dark", r > dark)
    }
}
