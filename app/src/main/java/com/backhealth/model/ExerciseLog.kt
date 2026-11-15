package com.backhealth.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_log")
data class ExerciseLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val exercise1Id: String,
    val exercise1Name: String,
    val exercise2Id: String,
    val exercise2Name: String,
    val completed: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
