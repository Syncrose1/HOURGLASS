package com.hourglass.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hourglass.core.Insights
import com.hourglass.data.repository.HourglassRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class InsightsViewModel @Inject constructor(
    repository: HourglassRepository
) : ViewModel() {

    val insights: StateFlow<Insights> = repository
        .observeSessionSummaries(dayOf = { millis -> localEpochDay(millis) })
        .map { summaries -> Insights.from(summaries, today = localEpochDay(now())) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = Insights.from(emptyList(), today = localEpochDay(now()))
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        fun now(): Long = System.currentTimeMillis()

        /**
         * Days since the epoch in the device's own timezone.
         *
         * Not `millis / MILLIS_PER_DAY`: that buckets by UTC, which puts an evening
         * session on tomorrow's bar for anyone east of Greenwich.
         */
        fun localEpochDay(millis: Long): Long {
            val calendar = Calendar.getInstance().apply { timeInMillis = millis }
            val offset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
            return TimeUnit.MILLISECONDS.toDays(millis + offset)
        }
    }
}
