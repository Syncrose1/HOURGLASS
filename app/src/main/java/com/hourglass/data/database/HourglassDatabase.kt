package com.hourglass.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hourglass.data.dao.QuicksandDao
import com.hourglass.data.dao.SettingsDao
import com.hourglass.data.dao.TaskDao
import com.hourglass.data.dao.TimerSessionDao
import com.hourglass.data.entity.QuicksandTaskEntity
import com.hourglass.data.entity.SettingsEntity
import com.hourglass.data.entity.TaskEntity
import com.hourglass.data.entity.TimerSessionEntity

@Database(
    entities = [
        TaskEntity::class,
        QuicksandTaskEntity::class,
        SettingsEntity::class,
        TimerSessionEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class HourglassDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun quicksandDao(): QuicksandDao
    abstract fun settingsDao(): SettingsDao
    abstract fun timerSessionDao(): TimerSessionDao

    companion object {
        const val NAME = "hourglass_database"
    }
}
