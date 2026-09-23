package com.hourglass.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.hourglass.data.entity.QuicksandTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuicksandDao {
    @Query("SELECT * FROM quicksand_tasks WHERE isActive = 1 ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<QuicksandTaskEntity>>

    @Query("SELECT * FROM quicksand_tasks WHERE id = :id")
    suspend fun getById(id: Long): QuicksandTaskEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: QuicksandTaskEntity): Long

    @Update
    suspend fun update(task: QuicksandTaskEntity)

    @Query("UPDATE quicksand_tasks SET isActive = 0 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT id FROM quicksand_tasks WHERE isActive = 1 AND createdAt < :time")
    suspend fun activeCreatedBefore(time: Long): List<Long>
}
