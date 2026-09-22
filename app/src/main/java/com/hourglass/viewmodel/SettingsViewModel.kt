package com.hourglass.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hourglass.core.Bedtime
import com.hourglass.core.TimeOfDay
import com.hourglass.data.repository.HourglassRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SleepSettings(
    val bedtime: TimeOfDay = TimeOfDay.DEFAULT_BEDTIME,
    val wakeTime: TimeOfDay = TimeOfDay.DEFAULT_WAKE
) {
    val sleepMinutes: Int get() = Bedtime.sleepDurationMinutes(bedtime, wakeTime)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: HourglassRepository
) : ViewModel() {

    val settings: StateFlow<SleepSettings> = repository.observeSettings()
        .map { stored ->
            SleepSettings(
                bedtime = TimeOfDay.parseOr(
                    stored[HourglassRepository.KEY_BEDTIME],
                    TimeOfDay.DEFAULT_BEDTIME
                ),
                wakeTime = TimeOfDay.parseOr(
                    stored[HourglassRepository.KEY_WAKE_TIME],
                    TimeOfDay.DEFAULT_WAKE
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SleepSettings()
        )

    fun setBedtime(value: TimeOfDay) = put(HourglassRepository.KEY_BEDTIME, value)

    fun setWakeTime(value: TimeOfDay) = put(HourglassRepository.KEY_WAKE_TIME, value)

    private fun put(key: String, value: TimeOfDay) {
        viewModelScope.launch { repository.putSetting(key, value.format()) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
