package com.hourglass.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hourglass.data.dao.SettingsDao
import com.hourglass.data.database.HourglassDatabase
import com.hourglass.data.entity.SettingsEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsDao = HourglassDatabase.getInstance(application).settingsDao()

    private val _bedtime = MutableStateFlow("22:00")
    val bedtime: StateFlow<String> = _bedtime.asStateFlow()

    private val _wakeTime = MutableStateFlow("07:00")
    val wakeTime: StateFlow<String> = _wakeTime.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDao.observeAll().collect { list ->
                val map = list.associate { it.key to it.value }
                map["bedtime"]?.let { _bedtime.value = it }
                map["wakeTime"]?.let { _wakeTime.value = it }
            }
        }
    }

    fun setBedtime(time: String) = viewModelScope.launch {
        _bedtime.value = time
        settingsDao.insert(SettingsEntity(key = "bedtime", value = time))
    }

    fun setWakeTime(time: String) = viewModelScope.launch {
        _wakeTime.value = time
        settingsDao.insert(SettingsEntity(key = "wakeTime", value = time))
    }

    fun getSleepDurationMinutes(): Int {
        val bedParts = _bedtime.value.split(":")
        val wakeParts = _wakeTime.value.split(":")
        if (bedParts.size != 2 || wakeParts.size != 2) return 8 * 60
        val bedHour = bedParts[0].toIntOrNull() ?: 22
        val bedMin = bedParts[1].toIntOrNull() ?: 0
        val wakeHour = wakeParts[0].toIntOrNull() ?: 7
        val wakeMin = wakeParts[1].toIntOrNull() ?: 0
        var bedTotal = bedHour * 60 + bedMin
        var wakeTotal = wakeHour * 60 + wakeMin
        if (wakeTotal <= bedTotal) wakeTotal += 24 * 60
        return wakeTotal - bedTotal
    }
}
