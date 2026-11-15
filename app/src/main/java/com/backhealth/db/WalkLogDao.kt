package com.backhealth.db

import androidx.room.*
import com.backhealth.model.WalkLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WalkLogDao {
    @Insert
    suspend fun insert(log: WalkLog): Long

    @Query("SELECT * FROM walk_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<WalkLog>>

    @Query("SELECT COUNT(*) FROM walk_log WHERE date = :date AND completed = 1")
    fun getCompletedCountForDate(date: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM walk_log WHERE completed = 1")
    suspend fun getTotalCompletedCount(): Int
}
