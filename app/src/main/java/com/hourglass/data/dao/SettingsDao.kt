package com.hourglass.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hourglass.data.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun get(key: String): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SettingsEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun remove(key: String)

    @Query("SELECT * FROM settings")
    fun observeAll(): Flow<List<SettingsEntity>>
}
