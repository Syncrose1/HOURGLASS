package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayLinesTest {

    @Test
    fun `the day splits at noon and five`() {
        assertEquals(DayLines.Part.MORNING, DayLines.partOf(6 * 60))
        assertEquals(DayLines.Part.MORNING, DayLines.partOf(11 * 60 + 59))
        assertEquals(DayLines.Part.AFTERNOON, DayLines.partOf(12 * 60))
        assertEquals(DayLines.Part.AFTERNOON, DayLines.partOf(16 * 60 + 59))
        assertEquals(DayLines.Part.EVENING, DayLines.partOf(17 * 60))
        assertEquals(DayLines.Part.EVENING, DayLines.partOf(23 * 60))
    }

    @Test
    fun `ten distinct lines for each part`() {
        DayLines.Part.entries.forEach { part ->
            val lines = DayLines.linesFor(part)
            assertEquals(10, lines.size)
            assertEquals(10, lines.distinct().size)
        }
    }

    @Test
    fun `a line holds for its part of the day and changes from day to day`() {
        val day = 20_000L
        assertEquals(DayLines.lineFor(12 * 60, day), DayLines.lineFor(16 * 60 + 45, day))
        assertNotEquals(DayLines.lineFor(13 * 60, day), DayLines.lineFor(13 * 60, day + 1))
        // Ten days in a row show all ten afternoon lines.
        val seen = (0 until 10).map { DayLines.lineFor(14 * 60, day + it) }.toSet()
        assertTrue(seen.size == 10)
    }
}
