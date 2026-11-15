package com.backhealth.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.backhealth.BackHealthApp
import com.backhealth.R
import com.backhealth.db.AppDatabase
import com.backhealth.ui.alarm.ExerciseAlarmActivity
import com.backhealth.ui.alarm.WalkAlarmActivity
import com.backhealth.utils.ExercisePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == AlarmScheduler.ACTION_REST_END) {
            // Wake up cycle service, notify rest complete & resume 20m countdown
            val serviceIntent = Intent(context, AlarmCycleService::class.java).apply {
                this.action = AlarmCycleService.ACTION_REST_EXPIRED
            }
            context.startService(serviceIntent)
            return
        }

        if (action == AlarmScheduler.ACTION_ALARM_TRIGGER) {
            val alarmType = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TYPE) ?: "EXERCISE"

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getInstance(context)
                    val prefs = db.userPrefsDao().getPrefs()

                    // Check if cycle is paused by user
                    if (prefs?.isCyclePaused == true) {
                        pendingResult.finish()
                        return@launch
                    }

                    // Check sleep window
                    val wakeTime = prefs?.wakeTime ?: "07:00"
                    val bedTime = prefs?.bedTime ?: "22:00"
                    val isAwake = SleepWindowChecker.isAwakeTime(wakeTime, bedTime)

                    if (!isAwake) {
                        // Reschedule for next morning
                        val nextWakeEpoch = SleepWindowChecker.getNextWakeUpEpochMillis(wakeTime)
                        AlarmScheduler.scheduleAlarm(context, nextWakeEpoch, "EXERCISE")
                        pendingResult.finish()
                        return@launch
                    }

                    // Trigger alarm based on type
                    when (alarmType) {
                        "EXERCISE", "SNOOZE_EXERCISE" -> {
                            val (ex1, ex2) = ExercisePicker.pickExercisesForMicroBreak(db.exerciseDao())
                            triggerExerciseAlarm(context, ex1?.id, ex1?.name, ex2?.id, ex2?.name)
                        }
                        "WALK", "SNOOZE_WALK" -> {
                            triggerWalkAlarm(context)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun triggerExerciseAlarm(
        context: Context,
        ex1Id: String?,
        ex1Name: String?,
        ex2Id: String?,
        ex2Name: String?
    ) {
        val fullScreenIntent = Intent(context, ExerciseAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ExerciseAlarmActivity.EXTRA_EX1_ID, ex1Id)
            putExtra(ExerciseAlarmActivity.EXTRA_EX1_NAME, ex1Name)
            putExtra(ExerciseAlarmActivity.EXTRA_EX2_ID, ex2Id)
            putExtra(ExerciseAlarmActivity.EXTRA_EX2_NAME, ex2Name)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            2001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, BackHealthApp.CHANNEL_ALARMS_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("🧘 Posture Reset Break")
            .setContentText("Micro-break: $ex1Name + $ex2Name")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setSound(soundUri)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_EXERCISE, notification)

        // Attempt direct launch if allowed
        try {
            context.startActivity(fullScreenIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerWalkAlarm(context: Context) {
        val fullScreenIntent = Intent(context, WalkAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            2002,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, BackHealthApp.CHANNEL_ALARMS_ID)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle("🚶 Time for a 5-Minute Walk")
            .setContentText("Decompress your lumbar spine with a quick walking break.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setSound(soundUri)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_WALK, notification)

        try {
            context.startActivity(fullScreenIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val NOTIFICATION_ID_EXERCISE = 3001
        const val NOTIFICATION_ID_WALK = 3002
    }
}
