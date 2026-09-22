package com.hourglass.di

import android.content.Context
import androidx.room.Room
import com.hourglass.data.dao.QuicksandDao
import com.hourglass.data.dao.SettingsDao
import com.hourglass.data.dao.TaskDao
import com.hourglass.data.dao.TimerSessionDao
import com.hourglass.data.database.HourglassDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the process-lifetime coroutine scope the timer engine ticks on. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HourglassDatabase =
        Room.databaseBuilder(context, HourglassDatabase::class.java, HourglassDatabase.NAME)
            // The app has never shipped a v1 database, so a dev install carrying the
            // older shape is rebuilt rather than migrated. Replace with a real
            // migration before the first release.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTaskDao(database: HourglassDatabase): TaskDao = database.taskDao()

    @Provides
    fun provideQuicksandDao(database: HourglassDatabase): QuicksandDao = database.quicksandDao()

    @Provides
    fun provideSettingsDao(database: HourglassDatabase): SettingsDao = database.settingsDao()

    @Provides
    fun provideSessionDao(database: HourglassDatabase): TimerSessionDao =
        database.timerSessionDao()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
