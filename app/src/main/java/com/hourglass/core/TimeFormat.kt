package com.hourglass.core

import java.util.Locale
import kotlin.math.abs

/**
 * Duration formatting for timer faces, notifications and labels.
 *
 * Deliberately free of Android dependencies so the rules that decide what a user
 * reads on the timer face can be unit tested on the JVM.
 */
object TimeFormat {

    /**
     * `H:MM:SS` once an hour is on the clock, `MM:SS` below that. The sign of
     * [millis] is ignored — use [signedClock] when a value may run negative.
     */
    fun clock(millis: Long): String {
        val totalSeconds = abs(millis) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Remaining time, with overtime shown as `+MM:SS` rather than clamped to zero.
     * A timer that has run past its allocation is the interesting case, so it gets
     * an explicit marker instead of silently sitting at `00:00`.
     */
    fun signedClock(remainingMillis: Long): String {
        val text = clock(remainingMillis)
        return if (remainingMillis < 0) "+$text" else text
    }

    /** Human phrasing for an allocation, e.g. `2h 30m`, `45m`, `30s`. */
    fun compact(millis: Long): String {
        val totalSeconds = abs(millis) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}
