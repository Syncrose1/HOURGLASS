package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerSandTest {

    @Test
    fun `tokens round trip`() {
        TimerSand.entries.forEach { sand ->
            assertEquals(sand, TimerSand.parse(sand.token))
        }
    }

    @Test
    fun `parsing is forgiving about case and padding`() {
        assertEquals(TimerSand.PLUM, TimerSand.parse("PLUM"))
        assertEquals(TimerSand.PLUM, TimerSand.parse("  plum  "))
    }

    @Test
    fun `hex values written by older builds snap to a slot`() {
        assertEquals(TimerSand.AMBER, TimerSand.parse("#FFE0A63C"))
        assertEquals(TimerSand.AMBER, TimerSand.parse("#E8A838"))
        assertEquals(TimerSand.AQUA, TimerSand.parse("#FF2F8F86"))
        assertEquals(TimerSand.OLIVE, TimerSand.parse("#7A9E3F"))
    }

    @Test
    fun `anything unrecognised falls back rather than throwing`() {
        assertEquals(TimerSand.DEFAULT, TimerSand.parse(null))
        assertEquals(TimerSand.DEFAULT, TimerSand.parse(""))
        assertEquals(TimerSand.DEFAULT, TimerSand.parse("chartreuse"))
        assertEquals(TimerSand.DEFAULT, TimerSand.parse("#123456"))
    }
}
