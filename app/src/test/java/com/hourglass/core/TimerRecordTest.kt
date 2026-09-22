package com.hourglass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerRecordTest {

    private val ref = TimerRef(3, TimerKind.QUICKSAND)
    private val start = 1_000_000L

    @Test
    fun `a running record accrues elapsed time from its anchor`() {
        val record = TimerRecord.started(ref, totalDurationMillis = 60_000, nowWallClock = start)
        assertTrue(record.isRunning)
        assertEquals(0L, record.elapsedAt(start))
        assertEquals(15_000L, record.elapsedAt(start + 15_000))
    }

    @Test
    fun `pausing banks elapsed time and freezes it`() {
        val paused = TimerRecord.started(ref, 60_000, start).paused(start + 20_000)
        assertFalse(paused.isRunning)
        assertEquals(20_000L, paused.elapsedAt(start + 20_000))
        assertEquals(20_000L, paused.elapsedAt(start + 999_000))
    }

    @Test
    fun `resuming continues from the banked total`() {
        val resumed = TimerRecord.started(ref, 60_000, start)
            .paused(start + 20_000)
            .resumed(start + 100_000)
        assertTrue(resumed.isRunning)
        assertEquals(20_000L, resumed.elapsedAt(start + 100_000))
        assertEquals(25_000L, resumed.elapsedAt(start + 105_000))
    }

    @Test
    fun `elapsed never runs backwards if the clock jumps back`() {
        val record = TimerRecord.started(ref, 60_000, start)
        assertEquals(0L, record.elapsedAt(start - 500_000))
    }

    @Test
    fun `encode and decode round trip`() {
        val record = TimerRecord.started(ref, 90_000, start).paused(start + 1_234)
        assertEquals(record, TimerRecord.decode(record.encode()))
    }

    @Test
    fun `decode rejects junk instead of throwing`() {
        assertNull(TimerRecord.decode(null))
        assertNull(TimerRecord.decode(""))
        assertNull(TimerRecord.decode("1|3|QUICKSAND"))
        assertNull(TimerRecord.decode("9|3|QUICKSAND|1|1|1|1"))
        assertNull(TimerRecord.decode("1|3|SANDSTORM|1|1|1|1"))
        assertNull(TimerRecord.decode("1|x|QUICKSAND|1|1|1|1"))
    }

    @Test
    fun `active timer derives remaining overtime and progress`() {
        val base = ActiveTimer(
            ref = ref,
            name = "Flashcards",
            colourHex = "#FFE0A63C",
            totalDurationMillis = 60_000,
            elapsedMillis = 15_000,
            isRunning = true,
            startedAt = start
        )
        assertEquals(45_000L, base.remainingMillis)
        assertFalse(base.isOvertime)
        assertEquals(0.25f, base.progress, 0.0001f)

        val over = base.copy(elapsedMillis = 75_000)
        assertTrue(over.isOvertime)
        assertEquals(15_000L, over.overtimeMillis)
        assertEquals(1f, over.progress, 0.0001f)
    }
}
