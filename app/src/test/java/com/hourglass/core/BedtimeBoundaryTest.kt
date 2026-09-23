package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BedtimeBoundaryTest {

    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    /** Millis for [hours] past midnight UTC on an arbitrary day. */
    private fun at(hours: Double) = 20_000 * day + (hours * hour).toLong()

    @Test
    fun `the day began at the most recent bedtime`() {
        val bedtime = TimeOfDay(23, 0)
        // Afternoon: the day began at last night's bedtime.
        assertEquals(at(-1.0), Bedtime.lastBoundary(at(15.0), bedtime, 0))
        // Half past midnight: bedtime was an hour and a half ago, so a new day.
        assertEquals(at(23.0), Bedtime.lastBoundary(at(24.5), bedtime, 0))
        // Exactly at bedtime the new day starts.
        assertEquals(at(23.0), Bedtime.lastBoundary(at(23.0), bedtime, 0))
    }

    @Test
    fun `a bedtime after midnight and a local offset both work`() {
        val late = TimeOfDay(1, 30)
        assertEquals(at(1.5), Bedtime.lastBoundary(at(9.0), late, 0))
        assertEquals(at(-22.5), Bedtime.lastBoundary(at(1.0), late, 0))
        // One hour ahead of UTC: 23:00 local is 22:00 UTC.
        assertEquals(at(22.0), Bedtime.lastBoundary(at(22.5), TimeOfDay(23, 0), hour))
    }
}
