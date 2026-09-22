package com.hourglass.timer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.hourglass.core.ActiveTimer
import com.hourglass.core.TimerRecord
import com.hourglass.core.TimerRef
import com.hourglass.data.repository.HourglassRepository
import com.hourglass.di.ApplicationScope
import com.hourglass.service.TimerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the one running timer.
 *
 * Previously the ticking lived in a `ViewModel` `Handler`, which meant the timer was
 * tied to a screen, was lost when the process was reclaimed, and re-sent a
 * `startForegroundService` intent ten times a second. Here the engine is a process
 * singleton: the UI renders [state] and calls the verbs, the foreground service renders
 * the same [state] into a notification, and the run is journalled so it survives being
 * swapped out or rebooted.
 */
@Singleton
class TimerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: HourglassRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<ActiveTimer?>(null)
    val state: StateFlow<ActiveTimer?> = _state.asStateFlow()

    /** Guards the start/pause/resume/stop transitions against overlapping taps. */
    private val mutex = Mutex()
    private var record: TimerRecord? = null
    private var ticker: Job? = null
    private var restored = false

    /**
     * Brings back the timer that was running when the process last went away.
     * Safe to call repeatedly; only the first call does any work.
     */
    fun restore() {
        scope.launch {
            mutex.withLock {
                if (restored) return@withLock
                restored = true
                val stored = repository.loadTimerRecord() ?: return@withLock
                val definition = repository.definition(stored.ref)
                if (definition == null) {
                    // The timer it points at has been archived — drop the stale journal.
                    repository.clearTimerRecord()
                    return@withLock
                }
                record = stored
                publish(stored, definition.name, definition.colourHex)
                if (stored.isRunning) {
                    startTicking()
                    startService()
                }
            }
        }
    }

    /** Starts [ref], stopping and filing whatever was running before it. */
    fun start(ref: TimerRef) {
        scope.launch {
            val definition = repository.definition(ref) ?: return@launch
            mutex.withLock {
                restored = true
                // Hand over rather than tear down: clearing the state, even for an
                // instant, would make the service shut itself down and the cards flash
                // idle before the new timer is published.
                stopLocked(fileSession = true, stopService = false, clearState = false)
                val now = System.currentTimeMillis()
                val fresh = TimerRecord.started(ref, definition.durationMillis, now)
                record = fresh
                repository.saveTimerRecord(fresh)
                publish(fresh, definition.name, definition.colourHex)
                startTicking()
                startService()
            }
        }
    }

    fun pause() {
        scope.launch {
            mutex.withLock {
                val current = record ?: return@withLock
                if (!current.isRunning) return@withLock
                ticker?.cancel()
                ticker = null
                val paused = current.paused(System.currentTimeMillis())
                record = paused
                repository.saveTimerRecord(paused)
                _state.value = _state.value?.copy(
                    elapsedMillis = paused.accumulatedMillis,
                    isRunning = false
                )
            }
        }
    }

    fun resume() {
        scope.launch {
            mutex.withLock {
                val current = record ?: return@withLock
                if (current.isRunning) return@withLock
                val resumed = current.resumed(System.currentTimeMillis())
                record = resumed
                repository.saveTimerRecord(resumed)
                _state.value = _state.value?.copy(isRunning = true)
                startTicking()
                startService()
            }
        }
    }

    /** Pause or resume, whichever the timer is not currently doing. */
    fun toggle() {
        val current = _state.value ?: return
        if (current.isRunning) pause() else resume()
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                stopLocked(fileSession = true, stopService = true, clearState = true)
            }
        }
    }

    private suspend fun stopLocked(
        fileSession: Boolean,
        stopService: Boolean,
        clearState: Boolean
    ) {
        ticker?.cancel()
        ticker = null
        val finishing = _state.value
        record = null
        if (clearState) _state.value = null
        repository.clearTimerRecord()
        if (fileSession && finishing != null) {
            repository.recordSession(finishing, System.currentTimeMillis())
        }
        if (stopService) context.stopService(Intent(context, TimerService::class.java))
    }

    private fun publish(record: TimerRecord, name: String, colourHex: String) {
        _state.value = ActiveTimer(
            ref = record.ref,
            name = name,
            colourHex = colourHex,
            totalDurationMillis = record.totalDurationMillis,
            elapsedMillis = record.elapsedAt(System.currentTimeMillis()),
            isRunning = record.isRunning,
            startedAt = record.startedAt
        )
    }

    private fun startTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val current = record ?: break
                if (!current.isRunning) break
                val elapsed = current.elapsedAt(System.currentTimeMillis())
                _state.value = _state.value?.copy(elapsedMillis = elapsed, isRunning = true)
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    private fun startService() {
        // Fire-and-forget: a user who declined the notification permission still gets a
        // working in-app timer, they just do not get the ongoing notification.
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TimerService::class.java).setAction(TimerService.ACTION_SHOW)
            )
        }
    }

    private companion object {
        /**
         * Four frames a second: fast enough that the seconds digit never looks late,
         * slow enough to stay off the battery. The notification samples this further
         * down to once a second.
         */
        const val TICK_INTERVAL_MILLIS = 250L
    }
}
