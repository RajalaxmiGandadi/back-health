package com.backhealth.utils

import com.backhealth.db.ExerciseDao
import com.backhealth.model.Exercise

object ExercisePicker {

    suspend fun pickExercisesForMicroBreak(exerciseDao: ExerciseDao): Pair<Exercise?, Exercise?> {
        val set1 = exerciseDao.getEnabledBySet("SET_1")
        val set2 = exerciseDao.getEnabledBySet("SET_2")

        val ex1 = if (set1.isNotEmpty()) set1.random() else null
        val ex2 = if (set2.isNotEmpty()) set2.random() else null

        return Pair(ex1, ex2)
    }
}
