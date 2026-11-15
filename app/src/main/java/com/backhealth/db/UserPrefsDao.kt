package com.backhealth.db

import androidx.room.*
import com.backhealth.model.UserPrefs
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPrefsDao {
    @Query("SELECT * FROM user_prefs WHERE id = 1")
    fun getPrefsFlow(): Flow<UserPrefs?>

    @Query("SELECT * FROM user_prefs WHERE id = 1")
    suspend fun getPrefs(): UserPrefs?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(prefs: UserPrefs)

    @Query("UPDATE user_prefs SET isCyclePaused = :paused WHERE id = 1")
    suspend fun setCyclePaused(paused: Boolean)
}
