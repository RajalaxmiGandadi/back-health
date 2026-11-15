package com.backhealth.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "walk_log")
data class WalkLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val durationMinutes: Int = 5,
    val completed: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
