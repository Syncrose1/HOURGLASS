package com.hourglass.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hourglass.core.Bedtime
import com.hourglass.core.Desert
import com.hourglass.core.Insights
import com.hourglass.core.TaskTotal
import com.hourglass.core.TimeFormat
import com.hourglass.core.TimeOfDay
import com.hourglass.data.repository.HourglassRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class DesertState(
    val desert: Desert = Desert.from(emptyList()),
    val insights: Insights = Insights.from(emptyList(), today = 0L),
    /** Tasks that account for the most sand, for the legend under the dune. */
    val topTasks: List<TaskTotal> = emptyList(),
    /** The sky turns over after bedtime. */
    val nightMode: Boolean = false
) {
    /** e.g. `42h banked · oasis at 100h` or `every landmark reached`. */
    fun milestoneLabel(): String {
        val next = desert.nextMilestone
            ?: return "${TimeFormat.compact(desert.totalMillis)} · every landmark reached"
        val name = next.name.lowercase().replace('_', ' ')
        return "${TimeFormat.compact(desert.totalMillis)} · $name at ${next.hours}h"
    }
}

@HiltViewModel
class DesertViewModel @Inject constructor(
    repository: HourglassRepository
) : ViewModel() {

    val state: StateFlow<DesertState> = combine(
        repository.observeSessionSummaries(limit = ALL_SESSIONS) { millis -> localEpochDay(millis) },
        repository.observeSettings()
    ) { summaries, settings ->
        val today = localEpochDay(System.currentTimeMillis())
        val insights = Insights.from(summaries, today = today)
        DesertState(
            desert = Desert.from(summaries),
            insights = insights,
            topTasks = insights.tasks.filterNot { it.isOther }.take(TOP_TASKS),
            nightMode = Bedtime.isPastBedtime(
                nowMinuteOfDay = nowMinuteOfDay(),
                bedtime = TimeOfDay.parseOr(
                    settings[HourglassRepository.KEY_BEDTIME],
                    TimeOfDay.DEFAULT_BEDTIME
                ),
                wake = TimeOfDay.parseOr(
                    settings[HourglassRepository.KEY_WAKE_TIME],
                    TimeOfDay.DEFAULT_WAKE
                )
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DesertState()
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val TOP_TASKS = 3

        /**
         * The dune is the whole history, not a recent window — that is the point of it.
         */
        const val ALL_SESSIONS = 5_000

        fun nowMinuteOfDay(): Int = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }

        /**
         * Days since the epoch in the device's own timezone. Not
         * `millis / MILLIS_PER_DAY`: that buckets by UTC, which puts an evening
         * session on tomorrow's bar for anyone east of Greenwich.
         */
        fun localEpochDay(millis: Long): Long {
            val calendar = Calendar.getInstance().apply { timeInMillis = millis }
            val offset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
            return TimeUnit.MILLISECONDS.toDays(millis + offset)
        }
    }
}
