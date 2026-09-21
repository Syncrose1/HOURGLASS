package com.hourglass.data.dao

import androidx.room.*
import com.hourglass.data.entity.QuicksandTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuicksandDao {
    @Query("SELECT * FROM quicksand_tasks WHERE isActive = 1 ORDER BY createdAt ASC")
    fun observeActiveTasks(): Flow<List<QuicksandTaskEntity>>

    @Query("SELECT * FROM quicksand_tasks WHERE id = :id")
    suspend fun getById(id: Long): QuicksandTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: QuicksandTaskEntity): Long

    @Update
    suspend fun update(task: QuicksandTaskEntity)

    @Delete
    suspend fun delete(task: QuicksandTaskEntity)

    @Query("UPDATE quicksand_tasks SET isActive = 0 WHERE id = :id")
    suspend fun archive(id: Long)
}
