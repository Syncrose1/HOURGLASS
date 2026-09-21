package com.hourglass.data.dao

import androidx.room.*
import com.hourglass.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE isActive = 1 ORDER BY createdAt ASC")
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("UPDATE tasks SET isActive = 0 WHERE id = :id")
    suspend fun archiveTask(id: Long)

    @Query("SELECT * FROM tasks WHERE createdAt >= :since")
    suspend fun getTasksSince(since: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentTasks(limit: Int): List<TaskEntity>
}
