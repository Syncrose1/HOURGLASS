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

class PastBedtimeTest {

    private val tenPm = TimeOfDay(22, 0)
    private val sevenAm = TimeOfDay(7, 0)

    private fun past(hour: Int, minute: Int = 0, bed: TimeOfDay = tenPm, wake: TimeOfDay = sevenAm) =
        Bedtime.isPastBedtime(TimeOfDay(hour, minute).minuteOfDay, bed, wake)

    @Test
    fun `the night wraps past midnight`() {
        assertTrue(past(22))
        assertTrue(past(23, 30))
        assertTrue(past(2))
        assertTrue(past(6, 59))
    }

    @Test
    fun `daytime is not past bedtime`() {
        assertFalse(past(7))
        assertFalse(past(12))
        assertFalse(past(21, 59))
    }

    @Test
    fun `a night that does not cross midnight still works`() {
        val bed = TimeOfDay(2, 0)
        val wake = TimeOfDay(9, 0)
        assertTrue(past(3, bed = bed, wake = wake))
        assertFalse(past(1, bed = bed, wake = wake))
        assertFalse(past(10, bed = bed, wake = wake))
        assertFalse(past(23, bed = bed, wake = wake))
    }
}

class DayRemainingTest {

    @Test
    fun `quarter hours floor rather than round`() {
        assertEquals(135, Bedtime.roundForDisplay(135))
        assertEquals(135, Bedtime.roundForDisplay(149))
        assertEquals(120, Bedtime.roundForDisplay(134))
        assertEquals(120, Bedtime.roundForDisplay(120))
        assertEquals(0, Bedtime.roundForDisplay(14))
        assertEquals(0, Bedtime.roundForDisplay(0))
        assertEquals(0, Bedtime.roundForDisplay(-30))
    }

    @Test
    fun `the figure is never larger than the time actually left`() {
        (0..1440).forEach { minutes ->
            assertTrue(Bedtime.roundForDisplay(minutes) <= minutes)
        }
    }

    @Test
    fun `minutes that feel the same read the same`() {
        // The whole point of the rounding: 2h13 and 2h07 are not different feelings.
        assertEquals(Bedtime.roundForDisplay(133), Bedtime.roundForDisplay(127))
        // And the step down to two hours lands as an event.
        assertEquals(135, Bedtime.roundForDisplay(136))
        assertEquals(120, Bedtime.roundForDisplay(134))
    }

    @Test
    fun `the display only ever counts down`() {
        var previous = Int.MAX_VALUE
        (1440 downTo 0).forEach { minutes ->
            val shown = Bedtime.roundForDisplay(minutes)
            assertTrue("went up at $minutes", shown <= previous)
            previous = shown
        }
    }

    @Test
    fun `wording is natural and names the day, not the bedtime`() {
        assertEquals("Your day ends in 2 hours 15 minutes", Bedtime.describeDayRemaining(139))
        assertEquals("Your day ends in 2 hours", Bedtime.describeDayRemaining(125))
        assertEquals("Your day ends in 45 minutes", Bedtime.describeDayRemaining(52))
        assertEquals("Your day ends in 1 hour", Bedtime.describeDayRemaining(60))
        assertEquals(Bedtime.DAY_OVER, Bedtime.describeDayRemaining(10))
        assertEquals(Bedtime.DAY_OVER, Bedtime.describeDayRemaining(0))
    }
}
