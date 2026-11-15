package com.backhealth.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.backhealth.BackHealthApp
import com.backhealth.R
import com.backhealth.ui.home.HomeActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmCycleService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_CYCLE

        when (action) {
            ACTION_START_CYCLE -> {
                if (_cycleState.value.nextAlarmEpochMillis == 0L) {
                    startNextAlarm(ALARM_TYPE_EXERCISE, INTERVAL_EXERCISE_MINUTES)
                }
            }
            ACTION_TASK_COMPLETED -> {
                val completedType = intent?.getStringExtra(EXTRA_COMPLETED_TYPE) ?: ALARM_TYPE_EXERCISE
                if (completedType == ALARM_TYPE_EXERCISE) {
                    // Next is walk alarm in 40 minutes!
                    startNextAlarm(ALARM_TYPE_WALK, INTERVAL_WALK_MINUTES)
                } else {
                    // Next is exercise alarm in 20 minutes!
                    startNextAlarm(ALARM_TYPE_EXERCISE, INTERVAL_EXERCISE_MINUTES)
                }
            }
            ACTION_SNOOZE -> {
                val currentType = intent?.getStringExtra(EXTRA_COMPLETED_TYPE) ?: _cycleState.value.nextAlarmType
                startNextAlarm(currentType, INTERVAL_SNOOZE_MINUTES, isSnooze = true)
            }
            ACTION_START_REST -> {
                val durationMinutes = intent?.getIntExtra(EXTRA_REST_MINUTES, 20) ?: 20
                startRestMode(durationMinutes)
            }
            ACTION_REST_EXPIRED, ACTION_CANCEL_REST -> {
                // Resume standard cycle starting with 20m exercise
                _cycleState.value = _cycleState.value.copy(isResting = false, restEndEpochMillis = 0L)
                startNextAlarm(ALARM_TYPE_EXERCISE, INTERVAL_EXERCISE_MINUTES)
            }
            ACTION_PAUSE_CYCLE -> {
                AlarmScheduler.cancelAlarms(this)
                _cycleState.value = _cycleState.value.copy(isPaused = true)
                updateForegroundNotification("Cycle Paused")
            }
            ACTION_RESUME_CYCLE -> {
                _cycleState.value = _cycleState.value.copy(isPaused = false)
                startNextAlarm(ALARM_TYPE_EXERCISE, INTERVAL_EXERCISE_MINUTES)
            }
        }

        return START_STICKY
    }

    private fun startNextAlarm(alarmType: String, delayMinutes: Int, isSnooze: Boolean = false) {
        val triggerEpoch = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
        AlarmScheduler.scheduleAlarm(this, triggerEpoch, alarmType)

        _cycleState.value = CycleState(
            nextAlarmType = alarmType,
            nextAlarmEpochMillis = triggerEpoch,
            isResting = false,
            isSnooze = isSnooze,
            isPaused = false
        )

        val typeLabel = if (alarmType == ALARM_TYPE_EXERCISE) "Posture Reset in $delayMinutes min" else "Walk Break in $delayMinutes min"
        val statusText = if (isSnooze) "Snoozed — re-alerting in $delayMinutes min" else "Next: $typeLabel"
        updateForegroundNotification(statusText)
    }

    private fun startRestMode(durationMinutes: Int) {
        AlarmScheduler.cancelAlarms(this)
        val restEndEpoch = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        AlarmScheduler.scheduleRestEnd(this, restEndEpoch)

        _cycleState.value = CycleState(
            nextAlarmType = ALARM_TYPE_EXERCISE,
            nextAlarmEpochMillis = 0L,
            isResting = true,
            restEndEpochMillis = restEndEpoch,
            isPaused = false
        )

        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(restEndEpoch))
        updateForegroundNotification("Resting until $timeStr (alarms muted)")
    }

    private fun updateForegroundNotification(content: String) {
        val notification = createNotification(content)
        startForeground(NOTIFICATION_ID_SERVICE, notification)
    }

    private fun createNotification(content: String): Notification {
        val launchIntent = Intent(this, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, BackHealthApp.CHANNEL_SERVICE_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("BackHealth Active")
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    data class CycleState(
        val nextAlarmType: String = ALARM_TYPE_EXERCISE,
        val nextAlarmEpochMillis: Long = 0L,
        val isResting: Boolean = false,
        val restEndEpochMillis: Long = 0L,
        val isSnooze: Boolean = false,
        val isPaused: Boolean = false
    )

    companion object {
        const val NOTIFICATION_ID_SERVICE = 4001

        const val ACTION_START_CYCLE = "ACTION_START_CYCLE"
        const val ACTION_TASK_COMPLETED = "ACTION_TASK_COMPLETED"
        const val ACTION_SNOOZE = "ACTION_SNOOZE"
        const val ACTION_START_REST = "ACTION_START_REST"
        const val ACTION_REST_EXPIRED = "ACTION_REST_EXPIRED"
        const val ACTION_CANCEL_REST = "ACTION_CANCEL_REST"
        const val ACTION_PAUSE_CYCLE = "ACTION_PAUSE_CYCLE"
        const val ACTION_RESUME_CYCLE = "ACTION_RESUME_CYCLE"

        const val EXTRA_COMPLETED_TYPE = "EXTRA_COMPLETED_TYPE"
        const val EXTRA_REST_MINUTES = "EXTRA_REST_MINUTES"

        const val ALARM_TYPE_EXERCISE = "EXERCISE"
        const val ALARM_TYPE_WALK = "WALK"

        const val INTERVAL_EXERCISE_MINUTES = 20
        const val INTERVAL_WALK_MINUTES = 40
        const val INTERVAL_SNOOZE_MINUTES = 2

        private val _cycleState = MutableStateFlow(CycleState())
        val cycleState: StateFlow<CycleState> = _cycleState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_START_CYCLE
            }
            context.startForegroundService(intent)
        }

        fun taskCompleted(context: Context, type: String) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_TASK_COMPLETED
                putExtra(EXTRA_COMPLETED_TYPE, type)
            }
            context.startService(intent)
        }

        fun snooze(context: Context, type: String) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_COMPLETED_TYPE, type)
            }
            context.startService(intent)
        }

        fun startRest(context: Context, minutes: Int) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_START_REST
                putExtra(EXTRA_REST_MINUTES, minutes)
            }
            context.startService(intent)
        }

        fun cancelRest(context: Context) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_CANCEL_REST
            }
            context.startService(intent)
        }

        fun pauseCycle(context: Context) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_PAUSE_CYCLE
            }
            context.startService(intent)
        }

        fun resumeCycle(context: Context) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_RESUME_CYCLE
            }
            context.startService(intent)
        }
    }
}
