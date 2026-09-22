package com.hourglass.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hourglass.core.ActiveTimer
import com.hourglass.core.TimerKind
import com.hourglass.core.TimerRef
import com.hourglass.data.repository.HourglassRepository
import com.hourglass.data.repository.TimerDefinition
import com.hourglass.timer.TimerController
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val colourHex: String,
    val durationMillis: Long,
    val remainingMillis: Long,
    /** Fraction of the allocation consumed, `0f..1f`. */
    val progress: Float,
    val isRunning: Boolean,
    val isPaused: Boolean,
    val isOvertime: Boolean,
    val sessionsCompleted: Int
) {
    val isIdle: Boolean get() = !isRunning && !isPaused
}

data class HomeState(
    val sandTimers: List<TimerCard> = emptyList(),
    val quicksand: List<TimerCard> = emptyList(),
    val focusedTodayMillis: Long = 0L
) {
    val isEmpty: Boolean get() = sandTimers.isEmpty() && quicksand.isEmpty()
}

@HiltViewModel
class HourglassViewModel @Inject constructor(
    private val repository: HourglassRepository,
    private val controller: TimerController
) : ViewModel() {

    val homeState: StateFlow<HomeState> = combine(
        repository.observeTimers(TimerKind.TASK),
        repository.observeTimers(TimerKind.QUICKSAND),
        controller.state,
        repository.observeRecentSessions()
    ) { tasks, quicksand, active, sessions ->
        val startOfDay = startOfToday()
        HomeState(
            sandTimers = tasks.map { it.toCard(active) },
            quicksand = quicksand.map { it.toCard(active) },
            focusedTodayMillis = sessions
                .filter { it.endedAt >= startOfDay }
                .sumOf { it.elapsedMillis } + (active?.takeIf { it.startedAt >= startOfDay }
                ?.elapsedMillis ?: 0L)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeState()
    )

    init {
        controller.restore()
    }

    fun start(ref: TimerRef) = controller.start(ref)

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun stop() = controller.stop()

    fun archive(ref: TimerRef) {
        viewModelScope.launch {
            if (controller.state.value?.ref == ref) controller.stop()
            repository.archive(ref)
        }
    }

    fun create(kind: TimerKind, name: String, hours: Int, minutes: Int, colourHex: String) {
        viewModelScope.launch {
            val duration = (hours * 3_600L + minutes * 60L) * 1_000L
            if (duration <= 0L || name.isBlank()) return@launch
            repository.create(kind, name.trim(), duration, colourHex)
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

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
        colourHex = colourHex,
        durationMillis = durationMillis,
        remainingMillis = live?.remainingMillis ?: durationMillis,
        progress = live?.progress ?: 0f,
        isRunning = live?.isRunning == true,
        isPaused = live?.isPaused == true,
        isOvertime = live?.isOvertime == true,
        sessionsCompleted = sessionsCompleted
    )
}
