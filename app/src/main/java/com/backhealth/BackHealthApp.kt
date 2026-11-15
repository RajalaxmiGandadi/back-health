package com.backhealth

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.backhealth.db.AppDatabase
import com.backhealth.repository.ExerciseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BackHealthApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var exerciseRepository: ExerciseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        exerciseRepository = ExerciseRepository(this, database.exerciseDao())

        createNotificationChannels()

        // Seed default exercises into Room asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            exerciseRepository.seedDefaultExercisesIfEmpty()
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Channel 1: High priority Alarms (Sound + Vibration + Fullscreen Intent)
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARMS_ID,
                getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_alarm_desc)
                enableVibration(true)
                setSound(alarmSound, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(alarmChannel)

            // Channel 2: Low priority ongoing Foreground Service
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_service_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ALARMS_ID = "channel_backhealth_alarms"
        const val CHANNEL_SERVICE_ID = "channel_backhealth_service"

        lateinit var instance: BackHealthApp
            private set
    }
}
