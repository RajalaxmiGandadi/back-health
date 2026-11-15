package com.backhealth.db

import androidx.room.*
import com.backhealth.model.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise ORDER BY sortOrder ASC")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercise WHERE setId = :setId AND enabled = 1")
    suspend fun getEnabledBySet(setId: String): List<Exercise>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun getById(id: String): Exercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Query("UPDATE exercise SET enabled = :enabled WHERE id = :id")
    suspend fun updateEnabled(id: String, enabled: Boolean)

    @Query("UPDATE exercise SET enabled = 1")
    suspend fun resetAllEnabled()

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int
}
