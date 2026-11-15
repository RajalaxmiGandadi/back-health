package com.backhealth.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise")
data class Exercise(
    @PrimaryKey
    val id: String,
    val name: String,
    val setId: String, // "SET_1" or "SET_2"
    val durationType: String, // "TIME" or "REPS"
    val durationValue: Int,
    val durationUnit: String,
    val coachingCue: String,
    val warning: String = "",
    val gifUrl: String = "",
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

data class ExerciseSet(
    val set_id: String,
    val set_name: String,
    val purpose: String,
    val exercises: List<Exercise>
)

data class ExerciseResponse(
    val version: Int,
    val sets: List<ExerciseSet>
)
