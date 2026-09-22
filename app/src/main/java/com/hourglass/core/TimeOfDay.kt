package com.hourglass.core

import java.util.Locale

/** A wall-clock time with no date attached, stored as `HH:mm`. */
data class TimeOfDay(val hour: Int, val minute: Int) {

    init {
        require(hour in 0..23) { "hour out of range: $hour" }
        require(minute in 0..59) { "minute out of range: $minute" }
    }

    val minuteOfDay: Int get() = hour * 60 + minute

    /** 24-hour form, always zero padded — the format persisted in settings. */
    fun format(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

    /** 12-hour form for display, e.g. `10:30 PM`. */
    fun formatFriendly(): String {
        val suffix = if (hour < 12) "AM" else "PM"
        val displayHour = when (hour % 12) {
            0 -> 12
            else -> hour % 12
        }
        return String.format(Locale.US, "%d:%02d %s", displayHour, minute, suffix)
    }

    companion object {
        val DEFAULT_BEDTIME = TimeOfDay(22, 0)
        val DEFAULT_WAKE = TimeOfDay(7, 0)

        /** Parses `HH:mm`, returning null for anything malformed or out of range. */
        fun parse(value: String?): TimeOfDay? {
            val parts = value?.split(":") ?: return null
            if (parts.size != 2) return null
            val hour = parts[0].trim().toIntOrNull() ?: return null
            val minute = parts[1].trim().toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return TimeOfDay(hour, minute)
        }

        fun parseOr(value: String?, fallback: TimeOfDay): TimeOfDay = parse(value) ?: fallback

        fun ofMinuteOfDay(minuteOfDay: Int): TimeOfDay {
            val wrapped = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return TimeOfDay(wrapped / 60, wrapped % 60)
        }

        const val MINUTES_PER_DAY = 24 * 60
    }
}
