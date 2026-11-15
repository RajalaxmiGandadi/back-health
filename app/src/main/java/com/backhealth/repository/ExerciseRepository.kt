package com.backhealth.repository

import android.content.Context
import com.backhealth.db.ExerciseDao
import com.backhealth.model.Exercise
import com.backhealth.model.ExerciseResponse
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(
    private val context: Context,
    private val exerciseDao: ExerciseDao
) {

    fun getAllExercises(): Flow<List<Exercise>> = exerciseDao.getAllExercises()

    suspend fun seedDefaultExercisesIfEmpty() {
        if (exerciseDao.count() == 0) {
            try {
                val jsonString = context.assets.open("exercises_default.json").bufferedReader().use { it.readText() }
                val response = Gson().fromJson(jsonString, ExerciseResponse::class.java)
                val allExercises = mutableListOf<Exercise>()
                response.sets.forEach { set ->
                    allExercises.addAll(set.exercises)
                }
                exerciseDao.insertAll(allExercises)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun setExerciseEnabled(id: String, enabled: Boolean) {
        exerciseDao.updateEnabled(id, enabled)
    }

    suspend fun resetAllExercises() {
        exerciseDao.resetAllEnabled()
    }
}
