package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BedtimeTest {

    private val tenPm = TimeOfDay(22, 0)
    private val sevenAm = TimeOfDay(7, 0)

    @Test
    fun `countdown wraps to tomorrow once bedtime has passed`() {
        // 23:30 -> next 22:00 is 22.5 hours away.
        assertEquals(22 * 60 + 30, Bedtime.minutesUntil(TimeOfDay(23, 30).minuteOfDay, tenPm))
    }

    @Test
    fun `countdown within the same day is a plain difference`() {
        assertEquals(90, Bedtime.minutesUntil(TimeOfDay(20, 30).minuteOfDay, tenPm))
    }

    @Test
    fun `landing exactly on bedtime rolls over rather than reporting zero`() {
        assertEquals(TimeOfDay.MINUTES_PER_DAY, Bedtime.minutesUntil(tenPm.minuteOfDay, tenPm))
    }

    @Test
    fun `sleep duration wraps over midnight`() {
        assertEquals(9 * 60, Bedtime.sleepDurationMinutes(tenPm, sevenAm))
        assertEquals(8 * 60, Bedtime.sleepDurationMinutes(TimeOfDay(23, 0), TimeOfDay(7, 0)))
        assertEquals(90, Bedtime.sleepDurationMinutes(TimeOfDay(1, 0), TimeOfDay(2, 30)))
    }

    @Test
    fun `wind down progress fills as bedtime approaches`() {
        assertEquals(0f, Bedtime.windDownProgress(5 * 60), 0.0001f)
        assertEquals(0f, Bedtime.windDownProgress(Bedtime.WIND_DOWN_MINUTES), 0.0001f)
        assertEquals(0.5f, Bedtime.windDownProgress(60), 0.0001f)
        assertEquals(1f, Bedtime.windDownProgress(0), 0.0001f)
    }

    @Test
    fun `wind down flag tracks the two hour window`() {
        assertTrue(Bedtime.isWindingDown(30))
        assertTrue(Bedtime.isWindingDown(Bedtime.WIND_DOWN_MINUTES))
        assertFalse(Bedtime.isWindingDown(Bedtime.WIND_DOWN_MINUTES + 1))
    }

    @Test
    fun `countdown wording is singular where it should be`() {
        assertEquals("1 hour 1 minute", Bedtime.describe(61))
        assertEquals("2 hours 30 minutes", Bedtime.describe(150))
        assertEquals("45 minutes", Bedtime.describe(45))
        assertEquals("3 hours", Bedtime.describe(180))
    }

    @Test
    fun `sleep projection is phrased in hours and minutes`() {
        assertEquals("8h", Bedtime.describeSleep(480))
        assertEquals("7h 45m", Bedtime.describeSleep(465))
    }
}
