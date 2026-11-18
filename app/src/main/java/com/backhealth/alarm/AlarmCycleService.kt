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
import com.backhealth.model.UserPrefs
import com.backhealth.ui.home.HomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmCycleService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_CYCLE

        when (action) {
            ACTION_START_CYCLE -> {
                restoreOrScheduleCycle()
            }
            ACTION_TASK_COMPLETED -> {
                AlarmScheduler.cancelAlarms(this)
                com.backhealth.utils.AlarmSoundManager.stop(this)
                val completedType = intent?.getStringExtra(EXTRA_COMPLETED_TYPE) ?: ALARM_TYPE_EXERCISE
                scheduleNextAfterTask(completedType)
            }
            ACTION_ALARM_FIRED -> {
                val firedType = intent?.getStringExtra(EXTRA_COMPLETED_TYPE) ?: _cycleState.value.nextAlarmType
                handleAlarmFired(firedType)
            }
            ACTION_ALARM_ACKNOWLEDGED -> {
                // User is actively viewing the alarm activity (doing exercises or walking)!
                // Cancel pending auto-snooze and silence sound so no outside alarm interrupts the active session
                AlarmScheduler.cancelAlarms(this)
                com.backhealth.utils.AlarmSoundManager.stop(this)
                val prefs = getSharedPreferences(PREFS_CYCLE, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_IS_SNOOZE, false).apply()
                _cycleState.value = _cycleState.value.copy(isSnooze = false)
                updateForegroundNotification("Active break in progress...")
            }
            ACTION_SNOOZE -> {
                com.backhealth.utils.AlarmSoundManager.stop(this)
                val currentType = intent?.getStringExtra(EXTRA_COMPLETED_TYPE) ?: _cycleState.value.nextAlarmType
                startNextAlarm(currentType, INTERVAL_SNOOZE_MINUTES, isSnooze = true)
            }
            ACTION_START_REST -> {
                val durationMinutes = intent?.getIntExtra(EXTRA_REST_MINUTES, 20) ?: 20
                startRestMode(durationMinutes)
            }
            ACTION_REST_EXPIRED, ACTION_CANCEL_REST -> {
                _cycleState.value = _cycleState.value.copy(isResting = false, restEndEpochMillis = 0L)
                scheduleInitialAlarm()
            }
            ACTION_PAUSE_CYCLE -> {
                AlarmScheduler.cancelAlarms(this)
                val prefs = getSharedPreferences(PREFS_CYCLE, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_IS_PAUSED, true).putLong(KEY_NEXT_ALARM_EPOCH, 0L).apply()
                _cycleState.value = _cycleState.value.copy(isPaused = true)
                updateForegroundNotification("Cycle Paused")
            }
            ACTION_RESUME_CYCLE -> {
                val prefs = getSharedPreferences(PREFS_CYCLE, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(KEY_IS_PAUSED, false).apply()
                _cycleState.value = _cycleState.value.copy(isPaused = false)
                scheduleInitialAlarm()
            }
            ACTION_RESET_USER_CYCLE -> {
                AlarmScheduler.cancelAlarms(this)
                val prefs = getSharedPreferences(PREFS_CYCLE, Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                _cycleState.value = CycleState()
                scheduleInitialAlarm()
            }
        }

        return START_STICKY
    }

    private fun restoreOrScheduleCycle() {
        val prefs = getSharedPreferences(PREFS_CYCLE, Context.MODE_PRIVATE)
        val savedEpoch = prefs.getLong(KEY_NEXT_ALARM_EPOCH, 0L)
        val savedType = prefs.getString(KEY_NEXT_ALARM_TYPE, null)
        val isResting = prefs.getBoolean(KEY_IS_RESTING, false)
        val restEndEpoch = prefs.getLong(KEY_REST_END_EPOCH, 0L)
        val isPaused = prefs.getBoolean(KEY_IS_PAUSED, false)

        val now = System.currentTimeMillis()

        if (isPaused) {
            _cycleState.value = CycleState(isPaused = true)
            updateForegroundNotification("Cycle Paused")
            return
        }

        if (isResting && restEndEpoch > now) {
            _cycleState.value = CycleState(
                nextAlarmType = savedType ?: ALARM_TYPE_EXERCISE,
                nextAlarmEpochMillis = 0L,
                isResting = true,
                restEndEpochMillis = restEndEpoch,
                isPaused = false
            )
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(restEndEpoch))
            updateForegroundNotification("Resting until $timeStr (alarms muted)")
            return
        }

        // If an active alarm is already scheduled in the future, retain it without restarting!
        if (savedEpoch > now && savedType != null) {
            _cycleState.value = CycleState(
                nextAlarmType = savedType,
                nextAlarmEpochMillis = savedEpoch,
                isResting = false,
                isSnooze = prefs.getBoolean(KEY_IS_SNOOZE, false),
                isPaused = false
            )
            val delayMin = maxOf(1L, (savedEpoch - now) / 60000)
            val typeLabel = if (savedType == ALARM_TYPE_EXERCISE) "Posture Reset in $delayMin min" else "Walk Break in $delayMin min"
            updateForegroundNotification("Next: $typeLabel")
            return
        }

        // Otherwise (first launch or previous alarm expired in the past), schedule fresh
        scheduleInitialAlarm()
    }

    private fun scheduleInitialAlarm() {
        CoroutineScope(Dispatchers.IO).launch {
            val db = (application as BackHealthApp).database
            val prefs = db.userPrefsDao().getPrefs()
            val isPreventive = prefs?.userType == UserPrefs.USER_TYPE_PREVENTIVE

            val wakeTime = prefs?.wakeTime ?: "07:00"
            val bedTime = prefs?.bedTime ?: "22:00"
            val isAwake = SleepWindowChecker.isAwakeTime(wakeTime, bedTime)
            val targetType = if (isPreventive) ALARM_TYPE_WALK else ALARM_TYPE_EXERCISE

            if (!isAwake) {
                val nextWakeEpoch = SleepWindowChecker.getNextWakeUpEpochMillis(wakeTime)
                scheduleDirectAtEpoch(targetType, nextWakeEpoch)
            } else {
                val delay = if (isPreventive) INTERVAL_PREVENTIVE_WALK_MINUTES else INTERVAL_EXERCISE_MINUTES
                startNextAlarm(targetType, delay)
            }
        }
    }

    private fun scheduleNextAfterTask(completedType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = (application as BackHealthApp).database
            val prefs = db.userPrefsDao().getPrefs()
            val isPreventive = prefs?.userType == UserPrefs.USER_TYPE_PREVENTIVE

            val wakeTime = prefs?.wakeTime ?: "07:00"
            val bedTime = prefs?.bedTime ?: "22:00"
            val isAwake = SleepWindowChecker.isAwakeTime(wakeTime, bedTime)

            val nextType = if (isPreventive) {
                ALARM_TYPE_WALK
            } else {
                if (completedType == ALARM_TYPE_EXERCISE) ALARM_TYPE_WALK else ALARM_TYPE_EXERCISE
            }

            if (!isAwake) {
                val nextWakeEpoch = SleepWindowChecker.getNextWakeUpEpochMillis(wakeTime)
                scheduleDirectAtEpoch(nextType, nextWakeEpoch)
            } else {
                val delay = if (isPreventive) {
                    INTERVAL_PREVENTIVE_WALK_MINUTES
                } else {
                    if (completedType == ALARM_TYPE_EXERCISE) INTERVAL_WALK_MINUTES else INTERVAL_EXERCISE_MINUTES
                }
                startNextAlarm(nextType, delay)
            }
        }
    }

    private fun scheduleDirectAtEpoch(alarmType: String, triggerEpoch: Long) {
        val prefs = getSharedPreferences(PREFS_CYCLE, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_NEXT_ALARM_EPOCH, triggerEpoch)
            .putString(KEY_NEXT_ALARM_TYPE, alarmType)
            .putBoolean(KEY_IS_RESTING, false)
            .putLong(KEY_REST_END_EPOCH, 0L)
            .putBoolean(KEY_IS_SNOOZE, false)
            .putBoolean(KEY_IS_PAUSED, false)
            .apply()

        AlarmScheduler.scheduleAlarm(this, triggerEpoch, alarmType)

        _cycleState.value = CycleState(
            nextAlarmType = alarmType,
            nextAlarmEpochMillis = triggerEpoch,
            isResting = false,
            isSnooze = false,
            isPaused = false
        )

        val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(triggerEpoch))
        updateForegroundNotification("Sleeping until $timeStr (alarms muted)")
    }

    private fun handleAlarmFired(firedType: String) {
        // Alarm has popped up on screen!
        // Schedule auto-snooze for INTERVAL_SNOOZE_MINUTES so that if user is away from phone,
        // it re-alerts every 1-2 minutes until marked completed
        val triggerEpoch = System.currentTimeMillis() + (INTERVAL_SNOOZE_MINUTES * 60 * 1000L)
        AlarmScheduler.scheduleAlarm(this, triggerEpoch, firedType)

        val prefs = getSharedPreferences(PREFS_CYCLE, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_NEXT_ALARM_EPOCH, triggerEpoch)
            .putString(KEY_NEXT_ALARM_TYPE, firedType)
            .putBoolean(KEY_IS_SNOOZE, true)
            .apply()

        _cycleState.value = _cycleState.value.copy(
            nextAlarmType = firedType,
            nextAlarmEpochMillis = triggerEpoch,
            isSnooze = true
        )
        updateForegroundNotification("Alarm triggered — re-alerting in $INTERVAL_SNOOZE_MINUTES min")
    }

    private fun startNextAlarm(alarmType: String, delayMinutes: Int, isSnooze: Boolean = false) {
        val triggerEpoch = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)

        // Persist to disk so navigating screens or app recreation never resets the countdown!
        val prefs = getSharedPreferences(PREFS_CYCLE, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_NEXT_ALARM_EPOCH, triggerEpoch)
            .putString(KEY_NEXT_ALARM_TYPE, alarmType)
            .putBoolean(KEY_IS_RESTING, false)
            .putLong(KEY_REST_END_EPOCH, 0L)
            .putBoolean(KEY_IS_SNOOZE, isSnooze)
            .putBoolean(KEY_IS_PAUSED, false)
            .apply()

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

        val prefs = getSharedPreferences(PREFS_CYCLE, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_NEXT_ALARM_EPOCH, 0L)
            .putBoolean(KEY_IS_RESTING, true)
            .putLong(KEY_REST_END_EPOCH, restEndEpoch)
            .putBoolean(KEY_IS_PAUSED, false)
            .apply()

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
        val launchIntent = Intent(this, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
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

        const val PREFS_CYCLE = "backhealth_cycle_prefs"
        const val KEY_NEXT_ALARM_EPOCH = "next_alarm_epoch"
        const val KEY_NEXT_ALARM_TYPE = "next_alarm_type"
        const val KEY_IS_RESTING = "is_resting"
        const val KEY_REST_END_EPOCH = "rest_end_epoch"
        const val KEY_IS_SNOOZE = "is_snooze"
        const val KEY_IS_PAUSED = "is_paused"

        const val ACTION_START_CYCLE = "ACTION_START_CYCLE"
        const val ACTION_TASK_COMPLETED = "ACTION_TASK_COMPLETED"
        const val ACTION_ALARM_FIRED = "ACTION_ALARM_FIRED"
        const val ACTION_ALARM_ACKNOWLEDGED = "ACTION_ALARM_ACKNOWLEDGED"
        const val ACTION_SNOOZE = "ACTION_SNOOZE"
        const val ACTION_START_REST = "ACTION_START_REST"
        const val ACTION_REST_EXPIRED = "ACTION_REST_EXPIRED"
        const val ACTION_CANCEL_REST = "ACTION_CANCEL_REST"
        const val ACTION_PAUSE_CYCLE = "ACTION_PAUSE_CYCLE"
        const val ACTION_RESUME_CYCLE = "ACTION_RESUME_CYCLE"
        const val ACTION_RESET_USER_CYCLE = "ACTION_RESET_USER_CYCLE"

        const val EXTRA_COMPLETED_TYPE = "EXTRA_COMPLETED_TYPE"
        const val EXTRA_REST_MINUTES = "EXTRA_REST_MINUTES"

        const val ALARM_TYPE_EXERCISE = "EXERCISE"
        const val ALARM_TYPE_WALK = "WALK"

        // Mode switch: Set to true for rapid testing (1m / 2m), false for production (20m / 40m)
        const val FAST_TESTING_MODE = false

        const val INTERVAL_EXERCISE_PROD_MINUTES = 20
        const val INTERVAL_WALK_PROD_MINUTES = 20
        const val INTERVAL_PREVENTIVE_WALK_PROD_MINUTES = 40
        const val INTERVAL_SNOOZE_PROD_MINUTES = 2

        const val INTERVAL_EXERCISE_TEST_MINUTES = 1
        const val INTERVAL_WALK_TEST_MINUTES = 1
        const val INTERVAL_PREVENTIVE_WALK_TEST_MINUTES = 2
        const val INTERVAL_SNOOZE_TEST_MINUTES = 1

        val INTERVAL_EXERCISE_MINUTES: Int
            get() = if (FAST_TESTING_MODE) INTERVAL_EXERCISE_TEST_MINUTES else INTERVAL_EXERCISE_PROD_MINUTES

        val INTERVAL_WALK_MINUTES: Int
            get() = if (FAST_TESTING_MODE) INTERVAL_WALK_TEST_MINUTES else INTERVAL_WALK_PROD_MINUTES

        val INTERVAL_PREVENTIVE_WALK_MINUTES: Int
            get() = if (FAST_TESTING_MODE) INTERVAL_PREVENTIVE_WALK_TEST_MINUTES else INTERVAL_PREVENTIVE_WALK_PROD_MINUTES

        val INTERVAL_SNOOZE_MINUTES: Int
            get() = if (FAST_TESTING_MODE) INTERVAL_SNOOZE_TEST_MINUTES else INTERVAL_SNOOZE_PROD_MINUTES

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

        fun alarmFired(context: Context, type: String) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_ALARM_FIRED
                putExtra(EXTRA_COMPLETED_TYPE, type)
            }
            context.startService(intent)
        }

        fun alarmAcknowledged(context: Context) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_ALARM_ACKNOWLEDGED
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

        fun resetUserCycle(context: Context) {
            val intent = Intent(context, AlarmCycleService::class.java).apply {
                action = ACTION_RESET_USER_CYCLE
            }
            context.startService(intent)
        }
    }
}
