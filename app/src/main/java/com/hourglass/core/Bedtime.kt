package com.hourglass.core

import com.hourglass.core.TimeOfDay.Companion.MINUTES_PER_DAY

/**
 * Bedtime arithmetic: everything the countdown card and the sleep projection need,
 * expressed in minutes so it can be tested without a clock.
 */
object Bedtime {

    /** The window, in minutes, over which the countdown card starts warming up. */
    const val WIND_DOWN_MINUTES = 2 * 60

    /**
     * Minutes from [nowMinuteOfDay] until the next occurrence of [bedtime].
     *
     * Returns a value in `1..1440`: landing exactly on bedtime reports a full day
     * rather than zero, because the countdown has genuinely rolled over to tomorrow.
     */
    fun minutesUntil(nowMinuteOfDay: Int, bedtime: TimeOfDay): Int {
        val diff = bedtime.minuteOfDay - nowMinuteOfDay
        return if (diff > 0) diff else diff + MINUTES_PER_DAY
    }

    /** Length of the planned sleep, wrapping over midnight when wake time is earlier. */
    fun sleepDurationMinutes(bedtime: TimeOfDay, wake: TimeOfDay): Int {
        val diff = wake.minuteOfDay - bedtime.minuteOfDay
        return if (diff > 0) diff else diff + MINUTES_PER_DAY
    }

    /**
     * How far the evening has run, as `0f..1f` across the last [WIND_DOWN_MINUTES]
     * before bed. Stays at 0 while bedtime is still distant and reaches 1 as it lands,
     * so the ring on the card fills up rather than draining away from the user.
     */
    fun windDownProgress(minutesUntilBedtime: Int): Float {
        if (minutesUntilBedtime >= WIND_DOWN_MINUTES) return 0f
        if (minutesUntilBedtime <= 0) return 1f
        return 1f - minutesUntilBedtime.toFloat() / WIND_DOWN_MINUTES
    }

    /** True once bedtime is inside the wind-down window. */
    fun isWindingDown(minutesUntilBedtime: Int): Boolean =
        minutesUntilBedtime <= WIND_DOWN_MINUTES

    /** Natural-language countdown, e.g. `2 hours 30 minutes`, `1 minute`. */
    fun describe(minutesUntilBedtime: Int): String {
        if (minutesUntilBedtime <= 0) return "Time for bed"
        val hours = minutesUntilBedtime / 60
        val minutes = minutesUntilBedtime % 60
        val hourText = if (hours == 1) "1 hour" else "$hours hours"
        val minuteText = if (minutes == 1) "1 minute" else "$minutes minutes"
        return when {
            hours == 0 -> minuteText
            minutes == 0 -> hourText
            else -> "$hourText $minuteText"
        }
    }

    /** Compact sleep projection, e.g. `8h 30m`. */
    fun describeSleep(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
    }
}
