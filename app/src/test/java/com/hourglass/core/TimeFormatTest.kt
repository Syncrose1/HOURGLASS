package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `clock drops the hour field below an hour`() {
        assertEquals("00:00", TimeFormat.clock(0))
        assertEquals("00:09", TimeFormat.clock(9_000))
        assertEquals("59:59", TimeFormat.clock(59 * 60_000 + 59_000))
    }

    @Test
    fun `clock shows hours once they are on the board`() {
        assertEquals("1:00:00", TimeFormat.clock(3_600_000))
        assertEquals("2:05:03", TimeFormat.clock(2 * 3_600_000 + 5 * 60_000 + 3_000))
    }

    @Test
    fun `overtime is reported with a sign instead of being clamped to zero`() {
        assertEquals("+00:30", TimeFormat.signedClock(-30_000))
        assertEquals("+1:00:01", TimeFormat.signedClock(-(3_600_000 + 1_000)))
        assertEquals("05:00", TimeFormat.signedClock(5 * 60_000))
    }

    @Test
    fun `compact phrasing drops empty units`() {
        assertEquals("2h 30m", TimeFormat.compact(2 * 3_600_000 + 30 * 60_000))
        assertEquals("2h", TimeFormat.compact(2 * 3_600_000))
        assertEquals("45m", TimeFormat.compact(45 * 60_000))
        assertEquals("30s", TimeFormat.compact(30_000))
    }
}
