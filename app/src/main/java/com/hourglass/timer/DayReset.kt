package com.hourglass.timer

import com.hourglass.core.Bedtime
import com.hourglass.core.TimeOfDay
import com.hourglass.core.TimerKind
import com.hourglass.data.repository.HourglassRepository
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The day ends at bedtime, and this is what ending it means.
 *
 * Quicksand timers are for the day they were made, so any made before the last bedtime
 * are retired; a paused session left over from before bedtime is filed, so its timer
 * starts the new day at zero. "Done" and the day's totals count from the same moment.
 *
 * Stateless and safe to run as often as anyone likes: it only ever acts on things
 * from before the last bedtime, so a second run finds nothing to do. A timer running
 * across bedtime is left alone and caught by the first run after it stops.
 */
@Singleton
class DayReset @Inject constructor(
    private val repository: HourglassRepository,
    private val controller: TimerController
) {
    /** When the current day began. */
    suspend fun boundary(nowMillis: Long = System.currentTimeMillis()): Long {
        val bedtime = TimeOfDay.parseOr(
            repository.getSetting(HourglassRepository.KEY_BEDTIME),
            TimeOfDay.DEFAULT_BEDTIME
        )
        return Bedtime.lastBoundary(nowMillis, bedtime, TimeZone.getDefault().getOffset(nowMillis).toLong())
    }

    suspend fun runIfDue(nowMillis: Long = System.currentTimeMillis()) {
        val boundary = boundary(nowMillis)
        controller.fileIfPausedBefore(boundary)
        val running = controller.state.value?.ref?.takeIf { it.kind == TimerKind.QUICKSAND }?.id
        repository.archiveQuicksandBefore(boundary, keep = running)
    }
}
