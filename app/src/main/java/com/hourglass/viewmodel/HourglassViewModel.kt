package com.hourglass.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hourglass.core.ActiveTimer
import com.hourglass.core.Bedtime
import com.hourglass.core.TimeOfDay
import com.hourglass.core.TimerKind
import com.hourglass.core.TimerRef
import com.hourglass.core.TimerSand
import com.hourglass.core.world.WorldKind
import com.hourglass.data.repository.HourglassRepository
import com.hourglass.data.repository.TimerDefinition
import com.hourglass.timer.TimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/** One timer as the home screen draws it. */
data class TimerCard(
    val ref: TimerRef,
    val name: String,
    val sand: TimerSand,
    val world: WorldKind = WorldKind.DEFAULT,
    val durationMillis: Long,
    val remainingMillis: Long,
    /** Fraction of the allocation consumed, `0f..1f`. */
    val progress: Float,
    val isRunning: Boolean,
    val isPaused: Boolean,
    val isOvertime: Boolean,
    val sessionsCompleted: Int,
    /** When the current session began, or null for a timer that is not in use. */
    val sessionStartedAt: Long? = null,
    /**
     * Today's allocation has been met: today's sessions add up to it, or the clock has
     * run into overtime. More is still welcome — this only says the day's share is in.
     */
    val isDoneToday: Boolean = false
) {
    val isIdle: Boolean get() = !isRunning && !isPaused

    /** How far through the allocation, unclamped: past 1 in overtime. */
    val timerProgress: Float
        get() = if (durationMillis <= 0) 0f
        else (durationMillis - remainingMillis).toFloat() / durationMillis
}

data class HomeState(
    val sandTimers: List<TimerCard> = emptyList(),
    val quicksand: List<TimerCard> = emptyList(),
    val focusedTodayMillis: Long = 0L,
    /** Minutes until bedtime, for the tile and the day's framing. */
    val minutesUntilBedtime: Int = 0,
    /**
     * True when a timer is running and the clock has crossed into the night.
     * Drives the one nudge the app ever gives unprompted.
     */
    val runningPastBedtime: Boolean = false,
    /** The timer that has taken over the screen, if any. */
    val focusedCard: TimerCard? = null
) {
    val isEmpty: Boolean get() = sandTimers.isEmpty() && quicksand.isEmpty()
}

@HiltViewModel
class HourglassViewModel @Inject constructor(
    private val repository: HourglassRepository,
    private val controller: TimerController
) : ViewModel() {

    /** Emitted once per timer that reaches its allocation; the UI answers with a haptic. */
    val completions: Flow<ActiveTimer> = controller.completions

    /**
     * The timer currently filling the screen.
     *
     * Focus is UI state, not timer state: a timer can be running with the app closed,
     * and closing the focus view pauses it rather than the other way round.
     */
    private val _focusedRef = MutableStateFlow<TimerRef?>(null)

    val homeState: StateFlow<HomeState> = combine(
        repository.observeTimers(TimerKind.TASK),
        repository.observeTimers(TimerKind.QUICKSAND),
        controller.state,
        repository.observeRecentSessions(),
        combine(repository.observeSettings(), _focusedRef) { settings, focused ->
            settings to focused
        }
    ) { tasks, quicksand, active, sessions, settingsAndFocus ->
        val (settings, focusedRef) = settingsAndFocus
        val startOfDay = startOfToday()
        val bedtime = TimeOfDay.parseOr(
            settings[HourglassRepository.KEY_BEDTIME],
            TimeOfDay.DEFAULT_BEDTIME
        )
        val wake = TimeOfDay.parseOr(
            settings[HourglassRepository.KEY_WAKE_TIME],
            TimeOfDay.DEFAULT_WAKE
        )
        val today = sessions.filter { it.endedAt >= startOfDay }
        fun done(card: TimerCard): TimerCard {
            val filed = today.filter {
                it.taskId == card.ref.id && it.isQuicksand == (card.ref.kind == TimerKind.QUICKSAND)
            }
            val live = active?.takeIf { it.ref == card.ref && it.startedAt >= startOfDay }?.elapsedMillis ?: 0L
            val met = filed.any { it.completed } ||
                filed.sumOf { it.elapsedMillis } + live >= card.durationMillis ||
                card.isOvertime
            return if (met) card.copy(isDoneToday = true) else card
        }
        val sandCards = tasks.map { done(it.toCard(active)) }
        val quickCards = quicksand.map { done(it.toCard(active)) }
        HomeState(
            sandTimers = sandCards,
            quicksand = quickCards,
            minutesUntilBedtime = Bedtime.minutesUntil(nowMinuteOfDay(), bedtime),
            focusedCard = focusedRef?.let { ref ->
                (sandCards + quickCards).firstOrNull { it.ref == ref }
            },
            focusedTodayMillis = sessions
                .filter { it.endedAt >= startOfDay }
                .sumOf { it.elapsedMillis } +
                (active?.takeIf { it.startedAt >= startOfDay }?.elapsedMillis ?: 0L),
            runningPastBedtime = active?.isRunning == true &&
                Bedtime.isPastBedtime(nowMinuteOfDay(), bedtime, wake)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeState()
    )

    init {
        controller.restore()
    }

    /**
     * Hands the screen to a timer and sets it running. Tapping a tile is the only way
     * to start anything, so opening and starting are deliberately the same action.
     */
    fun focus(ref: TimerRef) {
        _focusedRef.value = ref
        val active = controller.state.value
        when {
            active?.ref != ref -> controller.start(ref)
            active.isPaused -> controller.resume()
            else -> Unit // already running; just show it
        }
    }

    /** Tap in focus, or system back: pause and return to the wall. */
    fun pauseAndClose() {
        controller.pause()
        _focusedRef.value = null
    }

    /** Ends the session, banks it, and returns to the wall. */
    fun finish() {
        controller.stop()
        _focusedRef.value = null
    }

    fun stop() = controller.stop()

    fun archive(ref: TimerRef) {
        viewModelScope.launch {
            if (_focusedRef.value == ref) _focusedRef.value = null
            if (controller.state.value?.ref == ref) controller.stop()
            repository.archive(ref)
        }
    }

    fun create(
        kind: TimerKind,
        name: String,
        hours: Int,
        minutes: Int,
        sand: TimerSand,
        world: WorldKind
    ) {
        viewModelScope.launch {
            val duration = durationOf(hours, minutes)
            if (duration <= 0L || name.isBlank()) return@launch
            repository.create(kind, name.trim(), duration, sand, world)
        }
    }

    fun update(
        ref: TimerRef,
        name: String,
        hours: Int,
        minutes: Int,
        sand: TimerSand,
        world: WorldKind
    ) {
        viewModelScope.launch {
            val duration = durationOf(hours, minutes)
            if (duration <= 0L || name.isBlank()) return@launch
            repository.update(ref, name.trim(), duration, sand, world)
        }
    }

    suspend fun definition(ref: TimerRef): TimerDefinition? = repository.definition(ref)

    private fun durationOf(hours: Int, minutes: Int) = (hours * 3_600L + minutes * 60L) * 1_000L

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun nowMinuteOfDay(): Int = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * Folds the live timer into a stored definition. The comparison is on the whole
 * [TimerRef], so a quicksand timer never lights up its same-numbered sand-timer twin.
 */
private fun TimerDefinition.toCard(active: ActiveTimer?): TimerCard {
    val live = active?.takeIf { it.ref == ref }
    return TimerCard(
        ref = ref,
        name = name,
        sand = sand,
        world = world,
        durationMillis = durationMillis,
        remainingMillis = live?.remainingMillis ?: durationMillis,
        progress = live?.progress ?: 0f,
        isRunning = live?.isRunning == true,
        isPaused = live?.isPaused == true,
        isOvertime = live?.isOvertime == true,
        sessionsCompleted = sessionsCompleted,
        sessionStartedAt = live?.startedAt
    )
}
