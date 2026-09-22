package com.hourglass.core

/** Which of the two timer families a row belongs to. Ids are only unique within a kind. */
enum class TimerKind { TASK, QUICKSAND }

/**
 * Identifies a timer. Both tables auto-generate ids from 1, so an id on its own is
 * ambiguous — every lookup and every "is this the running one?" check needs the kind too.
 */
data class TimerRef(val id: Long, val kind: TimerKind)

/**
 * The live timer, as the UI and the notification see it.
 *
 * [elapsedMillis] is authoritative and only ever grows; remaining time is derived, so
 * running past the allocation simply produces a negative remainder rather than a
 * separate "overtime" mode that has to be kept in sync.
 */
data class ActiveTimer(
    val ref: TimerRef,
    val name: String,
    val colourHex: String,
    val totalDurationMillis: Long,
    val elapsedMillis: Long,
    val isRunning: Boolean,
    val startedAt: Long
) {
    val remainingMillis: Long get() = totalDurationMillis - elapsedMillis

    val isOvertime: Boolean get() = remainingMillis < 0

    val isPaused: Boolean get() = !isRunning

    val overtimeMillis: Long get() = if (isOvertime) -remainingMillis else 0L

    /** Fraction of the allocation consumed, clamped to `0f..1f` for drawing. */
    val progress: Float
        get() = if (totalDurationMillis <= 0) 1f
        else (elapsedMillis.toFloat() / totalDurationMillis.toFloat()).coerceIn(0f, 1f)
}

/**
 * The durable form of a running timer, persisted so a timer survives the app being
 * swapped out — or the device rebooting — instead of silently disappearing.
 *
 * Elapsed time is split into a paused accumulation plus a wall-clock anchor for the
 * current run, which is what lets [elapsedAt] reconstruct the true elapsed time after
 * the process has been gone for a while.
 */
data class TimerRecord(
    val id: Long,
    val kind: TimerKind,
    val totalDurationMillis: Long,
    val accumulatedMillis: Long,
    /** Wall-clock instant the current run began, or [NOT_RUNNING] while paused. */
    val runningSince: Long,
    val startedAt: Long
) {
    val ref: TimerRef get() = TimerRef(id, kind)

    val isRunning: Boolean get() = runningSince != NOT_RUNNING

    fun elapsedAt(nowWallClock: Long): Long =
        if (!isRunning) accumulatedMillis
        else accumulatedMillis + (nowWallClock - runningSince).coerceAtLeast(0L)

    fun paused(nowWallClock: Long): TimerRecord =
        if (!isRunning) this
        else copy(accumulatedMillis = elapsedAt(nowWallClock), runningSince = NOT_RUNNING)

    fun resumed(nowWallClock: Long): TimerRecord =
        if (isRunning) this else copy(runningSince = nowWallClock)

    fun encode(): String = listOf(
        VERSION,
        id,
        kind.name,
        totalDurationMillis,
        accumulatedMillis,
        runningSince,
        startedAt
    ).joinToString(FIELD_SEPARATOR)

    companion object {
        const val NOT_RUNNING = -1L
        private const val VERSION = 1
        private const val FIELD_SEPARATOR = "|"

        fun started(ref: TimerRef, totalDurationMillis: Long, nowWallClock: Long) = TimerRecord(
            id = ref.id,
            kind = ref.kind,
            totalDurationMillis = totalDurationMillis,
            accumulatedMillis = 0L,
            runningSince = nowWallClock,
            startedAt = nowWallClock
        )

        /** Returns null for absent, truncated or unrecognised data rather than throwing. */
        fun decode(value: String?): TimerRecord? {
            val parts = value?.split(FIELD_SEPARATOR) ?: return null
            if (parts.size != 7) return null
            if (parts[0].toIntOrNull() != VERSION) return null
            val kind = TimerKind.entries.firstOrNull { it.name == parts[2] } ?: return null
            return TimerRecord(
                id = parts[1].toLongOrNull() ?: return null,
                kind = kind,
                totalDurationMillis = parts[3].toLongOrNull() ?: return null,
                accumulatedMillis = parts[4].toLongOrNull() ?: return null,
                runningSince = parts[5].toLongOrNull() ?: return null,
                startedAt = parts[6].toLongOrNull() ?: return null
            )
        }
    }
}
