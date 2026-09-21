package com.hourglass.data.dao

import androidx.room.*
import com.hourglass.data.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE key = :key")
    suspend fun get(key: String): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SettingsEntity)

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingsEntity>>
}
