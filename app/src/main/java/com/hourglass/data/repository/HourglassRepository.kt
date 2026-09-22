package com.hourglass.data.repository

import com.hourglass.core.ActiveTimer
import com.hourglass.core.TimerKind
import com.hourglass.core.TimerRecord
import com.hourglass.core.TimerRef
import com.hourglass.core.TimerSand
import com.hourglass.core.SessionSummary
import com.hourglass.data.dao.QuicksandDao
import com.hourglass.data.dao.SettingsDao
import com.hourglass.data.dao.TaskDao
import com.hourglass.data.dao.TimerSessionDao
import com.hourglass.data.entity.QuicksandTaskEntity
import com.hourglass.data.entity.SettingsEntity
import com.hourglass.data.entity.TaskEntity
import com.hourglass.data.entity.TimerSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** A timer's identity and appearance, independent of which table it came from. */
data class TimerDefinition(
    val ref: TimerRef,
    val name: String,
    val sand: TimerSand,
    val durationMillis: Long,
    val totalTimeTracked: Long,
    val sessionsCompleted: Int,
    val sessionsOverrun: Int
)

/**
 * The single door onto stored state. Callers work in [TimerRef]s, so the fact that
 * sand timers and quicksand live in separate tables with independently numbered ids
 * never leaks out — which is what used to make a quicksand timer start its same-id
 * sand-timer twin.
 */
@Singleton
class HourglassRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val quicksandDao: QuicksandDao,
    private val sessionDao: TimerSessionDao,
    private val settingsDao: SettingsDao
) {

    fun observeTimers(kind: TimerKind): Flow<List<TimerDefinition>> = when (kind) {
        TimerKind.TASK -> taskDao.observeActive().map { rows -> rows.map(TaskEntity::toDefinition) }
        TimerKind.QUICKSAND ->
            quicksandDao.observeActive().map { rows -> rows.map(QuicksandTaskEntity::toDefinition) }
    }

    suspend fun definition(ref: TimerRef): TimerDefinition? = when (ref.kind) {
        TimerKind.TASK -> taskDao.getById(ref.id)?.toDefinition()
        TimerKind.QUICKSAND -> quicksandDao.getById(ref.id)?.toDefinition()
    }

    suspend fun create(
        kind: TimerKind,
        name: String,
        durationMillis: Long,
        sand: TimerSand
    ): TimerRef {
        val id = when (kind) {
            TimerKind.TASK -> taskDao.insert(
                TaskEntity(name = name, durationMillis = durationMillis, colour = sand.token)
            )
            TimerKind.QUICKSAND -> quicksandDao.insert(
                QuicksandTaskEntity(
                    name = name,
                    durationMillis = durationMillis,
                    colour = sand.token
                )
            )
        }
        return TimerRef(id, kind)
    }

    /**
     * Edits a timer in place. Running totals and session history are untouched: the
     * point of editing is to keep the history, otherwise the user would archive and
     * start again.
     */
    suspend fun update(ref: TimerRef, name: String, durationMillis: Long, sand: TimerSand) {
        when (ref.kind) {
            TimerKind.TASK -> taskDao.getById(ref.id)?.let {
                taskDao.update(it.copy(name = name, durationMillis = durationMillis, colour = sand.token))
            }
            TimerKind.QUICKSAND -> quicksandDao.getById(ref.id)?.let {
                quicksandDao.update(
                    it.copy(name = name, durationMillis = durationMillis, colour = sand.token)
                )
            }
        }
    }

    suspend fun archive(ref: TimerRef) = when (ref.kind) {
        TimerKind.TASK -> taskDao.archive(ref.id)
        TimerKind.QUICKSAND -> quicksandDao.archive(ref.id)
    }

    /**
     * Recent sessions, newest first. The window is generous because callers slice it
     * by date themselves — a day boundary evaluated per emission stays correct across
     * midnight, which a fixed query parameter would not.
     */
    fun observeRecentSessions(limit: Int = RECENT_SESSION_WINDOW): Flow<List<TimerSessionEntity>> =
        sessionDao.observeRecent(limit)

    /**
     * Recent sessions reduced to the pure form the insights layer works in.
     * [dayOf] converts an instant to a local epoch day — the caller owns the timezone.
     */
    fun observeSessionSummaries(
        limit: Int = RECENT_SESSION_WINDOW,
        dayOf: (Long) -> Long
    ): Flow<List<SessionSummary>> = sessionDao.observeRecent(limit).map { rows ->
        rows.map { row ->
            SessionSummary(
                epochDay = dayOf(row.endedAt),
                taskName = row.taskName,
                sand = TimerSand.parse(row.colour),
                elapsedMillis = row.elapsedMillis,
                overtimeMillis = row.overtimeMillis,
                completed = row.completed
            )
        }
    }

    /**
     * Files a finished run against both the session log and the timer's running totals.
     * Runs shorter than [MINIMUM_RECORDED_MILLIS] are dropped so an accidental
     * start-then-stop does not pollute the history.
     */
    suspend fun recordSession(timer: ActiveTimer, endedAt: Long) {
        if (timer.elapsedMillis < MINIMUM_RECORDED_MILLIS) return

        val overtime = max(0L, timer.elapsedMillis - timer.totalDurationMillis)
        val completed = timer.elapsedMillis >= timer.totalDurationMillis

        sessionDao.insert(
            TimerSessionEntity(
                taskId = timer.ref.id,
                taskName = timer.name,
                isQuicksand = timer.ref.kind == TimerKind.QUICKSAND,
                plannedDurationMillis = timer.totalDurationMillis,
                elapsedMillis = timer.elapsedMillis,
                overtimeMillis = overtime,
                startedAt = timer.startedAt,
                endedAt = endedAt,
                completed = completed,
                colour = timer.sand.token
            )
        )

        when (timer.ref.kind) {
            TimerKind.TASK -> taskDao.getById(timer.ref.id)?.let { task ->
                taskDao.update(
                    task.copy(
                        totalTimeTracked = task.totalTimeTracked + timer.elapsedMillis,
                        totalOvertimeMillis = task.totalOvertimeMillis + overtime,
                        sessionsCompleted = task.sessionsCompleted + if (completed) 1 else 0,
                        sessionsOverrun = task.sessionsOverrun + if (overtime > 0) 1 else 0,
                        lastCompletedAt = endedAt
                    )
                )
            }
            TimerKind.QUICKSAND -> quicksandDao.getById(timer.ref.id)?.let { quicksand ->
                quicksandDao.update(
                    quicksand.copy(
                        totalTimeTracked = quicksand.totalTimeTracked + timer.elapsedMillis,
                        totalOvertimeMillis = quicksand.totalOvertimeMillis + overtime,
                        sessionsCompleted = quicksand.sessionsCompleted + if (completed) 1 else 0,
                        sessionsOverrun = quicksand.sessionsOverrun + if (overtime > 0) 1 else 0,
                        lastCompletedAt = endedAt
                    )
                )
            }
        }
    }

    // --- settings ---------------------------------------------------------

    fun observeSettings(): Flow<Map<String, String>> =
        settingsDao.observeAll().map { rows -> rows.associate { it.key to it.value } }

    suspend fun putSetting(key: String, value: String) =
        settingsDao.put(SettingsEntity(key = key, value = value))

    suspend fun getSetting(key: String): String? = settingsDao.get(key)?.value

    // --- the timer that was running when the process last went away -------

    suspend fun loadTimerRecord(): TimerRecord? =
        TimerRecord.decode(settingsDao.get(KEY_ACTIVE_TIMER)?.value)

    suspend fun saveTimerRecord(record: TimerRecord) =
        settingsDao.put(SettingsEntity(KEY_ACTIVE_TIMER, record.encode()))

    suspend fun clearTimerRecord() = settingsDao.remove(KEY_ACTIVE_TIMER)

    companion object {
        const val KEY_BEDTIME = "bedtime"
        const val KEY_WAKE_TIME = "wakeTime"
        private const val KEY_ACTIVE_TIMER = "activeTimer"

        /** Below one second a run is a misfire, not a session. */
        private const val MINIMUM_RECORDED_MILLIS = 1_000L

        /** Comfortably more sessions than a single day can hold. */
        private const val RECENT_SESSION_WINDOW = 200
    }
}

private fun TaskEntity.toDefinition() = TimerDefinition(
    ref = TimerRef(id, TimerKind.TASK),
    name = name,
    sand = TimerSand.parse(colour),
    durationMillis = durationMillis,
    totalTimeTracked = totalTimeTracked,
    sessionsCompleted = sessionsCompleted,
    sessionsOverrun = sessionsOverrun
)

private fun QuicksandTaskEntity.toDefinition() = TimerDefinition(
    ref = TimerRef(id, TimerKind.QUICKSAND),
    name = name,
    sand = TimerSand.parse(colour),
    durationMillis = durationMillis,
    totalTimeTracked = totalTimeTracked,
    sessionsCompleted = sessionsCompleted,
    sessionsOverrun = sessionsOverrun
)
