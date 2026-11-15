package com.backhealth.db

import androidx.room.*
import com.backhealth.model.ExerciseExclusion
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseExclusionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exclusion: ExerciseExclusion)

    @Query("DELETE FROM exercise_exclusion WHERE exerciseId = :exerciseId")
    suspend fun delete(exerciseId: String)

    @Query("DELETE FROM exercise_exclusion")
    suspend fun deleteAll()

    @Query("SELECT * FROM exercise_exclusion")
    fun getAllExclusions(): Flow<List<ExerciseExclusion>>
}
