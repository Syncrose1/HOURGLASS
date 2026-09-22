package com.hourglass.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key/value store for preferences and the timer that was running at shutdown. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
