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

    /**
     * True when the current time falls inside the night — after bedtime, before waking.
     *
     * "Bank your time, respect your rest" cuts both ways: the app is as willing to say
     * stop as it is to say start, and this is what lets a running timer say so.
     */
    fun isPastBedtime(nowMinuteOfDay: Int, bedtime: TimeOfDay, wake: TimeOfDay): Boolean {
        val bed = bedtime.minuteOfDay
        val rise = wake.minuteOfDay
        return if (bed <= rise) {
            // A night that does not cross midnight, e.g. a 02:00 bedtime and 07:00 wake.
            nowMinuteOfDay in bed until rise
        } else {
            nowMinuteOfDay >= bed || nowMinuteOfDay < rise
        }
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

    /**
     * The granularity the day's remaining time is reported at.
     *
     * Minute-by-minute precision reads as noise: "2 hours 13 minutes" and "2 hours 7
     * minutes" are indistinguishable as feelings, so the countdown spends its
     * resolution on impact instead. Quarter-hours make the display step, and a step
     * from "2 hours 15 minutes" to "2 hours" registers as something actually lost.
     */
    const val ROUNDING_MINUTES = 15

    /**
     * Floors [minutes] to a quarter-hour.
     *
     * Floor, not nearest: a countdown that rounds up tells you that you have time you
     * do not have, and this app has no business doing that. Flooring means the figure
     * is a promise the clock can keep — you always have at least what it says.
     */
    fun roundForDisplay(minutes: Int): Int =
        if (minutes <= 0) 0 else (minutes / ROUNDING_MINUTES) * ROUNDING_MINUTES

    /**
     * The persistent notification's wording, e.g. `Your day ends in 2 hours 15 minutes`.
     * Phrased as the day ending rather than as a bedtime, because a bedtime is
     * something you can negotiate with and the end of a day is not.
     */
    fun describeDayRemaining(minutesUntilBedtime: Int): String {
        val rounded = roundForDisplay(minutesUntilBedtime)
        if (rounded <= 0) return DAY_OVER
        return "Your day ends in ${describe(rounded)}"
    }

    const val DAY_OVER = "Your day is over"

    /** Compact sleep projection, e.g. `8h 30m`. */
    fun describeSleep(minutes: Int): String {
        val hours = minutes / 60
        val rest = minutes % 60
        return if (rest == 0) "${hours}h" else "${hours}h ${rest}m"
    }
}
