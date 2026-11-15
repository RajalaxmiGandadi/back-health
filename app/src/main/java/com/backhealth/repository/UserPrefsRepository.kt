package com.backhealth.repository

import com.backhealth.db.UserPrefsDao
import com.backhealth.model.UserPrefs
import kotlinx.coroutines.flow.Flow

class UserPrefsRepository(private val userPrefsDao: UserPrefsDao) {

    fun getPrefsFlow(): Flow<UserPrefs?> = userPrefsDao.getPrefsFlow()

    suspend fun getPrefs(): UserPrefs {
        return userPrefsDao.getPrefs() ?: UserPrefs().also {
            userPrefsDao.insertOrUpdate(it)
        }
    }

    suspend fun savePrefs(prefs: UserPrefs) {
        userPrefsDao.insertOrUpdate(prefs)
    }

    suspend fun setCyclePaused(paused: Boolean) {
        userPrefsDao.setCyclePaused(paused)
    }
}
