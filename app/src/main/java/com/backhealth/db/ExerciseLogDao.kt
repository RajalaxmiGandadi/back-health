package com.backhealth.db

import androidx.room.*
import com.backhealth.model.ExerciseLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseLogDao {
    @Insert
    suspend fun insert(log: ExerciseLog): Long

    @Query("SELECT * FROM exercise_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ExerciseLog>>

    @Query("SELECT COUNT(*) FROM exercise_log WHERE date = :date AND completed = 1")
    fun getCompletedCountForDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM exercise_log WHERE completed = 1")
    suspend fun getTotalCompletedCount(): Int

    @Query("SELECT DISTINCT date FROM exercise_log WHERE completed = 1 ORDER BY date DESC")
    suspend fun getActiveDates(): List<String>
}
