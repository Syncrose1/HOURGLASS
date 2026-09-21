package com.hourglass.data.dao

import androidx.room.*
import com.hourglass.data.entity.TimerSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerSessionDao {
    @Query("SELECT * FROM timer_sessions ORDER BY endedAt DESC")
    fun observeSessions(): Flow<List<TimerSessionEntity>>

    @Query("SELECT * FROM timer_sessions WHERE taskId = :taskId ORDER BY endedAt DESC")
    fun observeSessionsForTask(taskId: Long): Flow<List<TimerSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: TimerSessionEntity): Long

    @Query("SELECT COUNT(*) FROM timer_sessions WHERE taskId = :taskId")
    suspend fun countSessionsForTask(taskId: Long): Int

    @Query("SELECT SUM(elapsedMillis) FROM timer_sessions WHERE taskId = :taskId")
    suspend fun totalTrackedMillisForTask(taskId: Long): Long?
}
