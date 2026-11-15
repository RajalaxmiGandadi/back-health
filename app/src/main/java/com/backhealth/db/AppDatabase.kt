package com.backhealth.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.backhealth.model.*

@Database(
    entities = [
        Exercise::class,
        ExerciseLog::class,
        WalkLog::class,
        UserPrefs::class,
        ExerciseExclusion::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun walkLogDao(): WalkLogDao
    abstract fun userPrefsDao(): UserPrefsDao
    abstract fun exerciseExclusionDao(): ExerciseExclusionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "backhealth.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
