package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeOfDayTest {

    @Test
    fun `parses zero padded and bare values`() {
        assertEquals(TimeOfDay(22, 0), TimeOfDay.parse("22:00"))
        assertEquals(TimeOfDay(7, 5), TimeOfDay.parse("7:5"))
    }

    @Test
    fun `rejects malformed and out of range values`() {
        assertNull(TimeOfDay.parse(null))
        assertNull(TimeOfDay.parse(""))
        assertNull(TimeOfDay.parse("2200"))
        assertNull(TimeOfDay.parse("24:00"))
        assertNull(TimeOfDay.parse("22:60"))
        assertNull(TimeOfDay.parse("ab:cd"))
        assertNull(TimeOfDay.parse("22:00:00"))
    }

    @Test
    fun `formats for storage and for display`() {
        assertEquals("07:05", TimeOfDay(7, 5).format())
        assertEquals("7:05 AM", TimeOfDay(7, 5).formatFriendly())
        assertEquals("12:00 AM", TimeOfDay(0, 0).formatFriendly())
        assertEquals("12:30 PM", TimeOfDay(12, 30).formatFriendly())
        assertEquals("10:00 PM", TimeOfDay(22, 0).formatFriendly())
    }

    @Test
    fun `minute of day round trips`() {
        assertEquals(TimeOfDay(13, 45), TimeOfDay.ofMinuteOfDay(13 * 60 + 45))
        assertEquals(TimeOfDay(0, 0), TimeOfDay.ofMinuteOfDay(TimeOfDay.MINUTES_PER_DAY))
    }
}
