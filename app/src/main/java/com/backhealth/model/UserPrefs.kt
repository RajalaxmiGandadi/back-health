package com.backhealth.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_prefs")
data class UserPrefs(
    @PrimaryKey
    val id: Int = 1,
    val userType: String = USER_TYPE_DISC_PATIENT, // "DISC_PATIENT" or "PREVENTIVE"
    val wakeTime: String = "07:00", // HH:mm
    val bedTime: String = "22:00", // HH:mm
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val isOnboardingComplete: Boolean = false,
    val isCyclePaused: Boolean = false
) {
    companion object {
        const val USER_TYPE_DISC_PATIENT = "DISC_PATIENT"
        const val USER_TYPE_PREVENTIVE = "PREVENTIVE"
    }
}

@Entity(tableName = "exercise_exclusion")
data class ExerciseExclusion(
    @PrimaryKey
    val exerciseId: String,
    val reason: String = "USER_DISABLED",
    val timestamp: Long = System.currentTimeMillis()
)
