package com.hourglass.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hourglass.data.entity.TimerSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerSessionDao {
    @Query("SELECT * FROM timer_sessions ORDER BY endedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TimerSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: TimerSessionEntity): Long
}
