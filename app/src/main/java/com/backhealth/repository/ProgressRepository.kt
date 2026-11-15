package com.backhealth.repository

import com.backhealth.db.ExerciseLogDao
import com.backhealth.db.WalkLogDao
import com.backhealth.model.ExerciseLog
import com.backhealth.model.WalkLog
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ProgressRepository(
    private val exerciseLogDao: ExerciseLogDao,
    private val walkLogDao: WalkLogDao
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun getTodayDate(): String = dateFormat.format(Date())

    fun getTodayExercisesCount(): Flow<Int> = exerciseLogDao.getCompletedCountForDate(getTodayDate())

    fun getTodayWalksCount(): Flow<Int> = walkLogDao.getCompletedCountForDate(getTodayDate())

    fun getAllExerciseLogs(): Flow<List<ExerciseLog>> = exerciseLogDao.getAllLogs()

    fun getAllWalkLogs(): Flow<List<WalkLog>> = walkLogDao.getAllLogs()

    suspend fun recordExerciseCompleted(
        ex1Id: String,
        ex1Name: String,
        ex2Id: String,
        ex2Name: String
    ) {
        val log = ExerciseLog(
            date = getTodayDate(),
            exercise1Id = ex1Id,
            exercise1Name = ex1Name,
            exercise2Id = ex2Id,
            exercise2Name = ex2Name,
            completed = true
        )
        exerciseLogDao.insert(log)
    }

    suspend fun recordWalkCompleted(durationMinutes: Int = 5) {
        val log = WalkLog(
            date = getTodayDate(),
            durationMinutes = durationMinutes,
            completed = true
        )
        walkLogDao.insert(log)
    }

    suspend fun getTotalExercises(): Int = exerciseLogDao.getTotalCompletedCount()

    suspend fun getTotalWalks(): Int = walkLogDao.getTotalCompletedCount()

    /**
     * Calculates consecutive days streak of doing back health routines.
     */
    suspend fun calculateCurrentStreak(): Int {
        val activeDates = exerciseLogDao.getActiveDates().toSet()
        if (activeDates.isEmpty()) return 0

        var streak = 0
        val cal = Calendar.getInstance()

        // Check today first
        val todayStr = dateFormat.format(cal.time)
        if (activeDates.contains(todayStr)) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            // Check if streak was maintained yesterday
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = dateFormat.format(cal.time)
            if (!activeDates.contains(yesterdayStr)) {
                return 0
            }
        }

        while (true) {
            val dateStr = dateFormat.format(cal.time)
            if (activeDates.contains(dateStr)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return maxOf(streak, 1)
    }
}
