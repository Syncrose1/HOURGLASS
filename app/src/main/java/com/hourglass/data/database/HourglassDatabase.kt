package com.hourglass.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.hourglass.data.dao.QuicksandDao
import com.hourglass.data.dao.SettingsDao
import com.hourglass.data.dao.TimerSessionDao
import com.hourglass.data.dao.TaskDao
import com.hourglass.data.entity.QuicksandTaskEntity
import com.hourglass.data.entity.SettingsEntity
import com.hourglass.data.entity.TimerSessionEntity
import com.hourglass.data.entity.TaskEntity

@Database(
    entities = [TaskEntity::class, QuicksandTaskEntity::class, SettingsEntity::class, TimerSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HourglassDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun quicksandDao(): QuicksandDao
    abstract fun settingsDao(): SettingsDao
    abstract fun timerSessionDao(): TimerSessionDao

    companion object {
        @Volatile
        private var INSTANCE: HourglassDatabase? = null

        fun getInstance(context: Context): HourglassDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HourglassDatabase::class.java,
                    "hourglass_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
