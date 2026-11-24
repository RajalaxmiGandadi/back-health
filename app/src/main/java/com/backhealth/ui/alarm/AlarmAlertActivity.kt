package com.backhealth.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.backhealth.alarm.AlarmCycleService
import com.backhealth.databinding.ActivityAlarmAlertBinding
import com.backhealth.utils.AlarmSoundManager

class AlarmAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmAlertBinding
    private var alarmType: String = AlarmCycleService.ALARM_TYPE_EXERCISE
    private var isPreventive: Boolean = false
    private var intervalMinutes: Int = 20
    private var ex1Id: String? = null
    private var ex1Name: String? = null
    private var ex2Id: String? = null
    private var ex2Name: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockscreen()
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractExtras()
        setupUI()
        setupListeners()

        // Start playing alarm ringtone & continuous vibration immediately
        AlarmSoundManager.playAlarm(this)
    }

    private fun extractExtras() {
        alarmType = intent.getStringExtra(EXTRA_ALARM_TYPE) ?: AlarmCycleService.ALARM_TYPE_EXERCISE
        isPreventive = intent.getBooleanExtra(EXTRA_IS_PREVENTIVE, false)
        intervalMinutes = intent.getIntExtra(EXTRA_INTERVAL_MINUTES, if (isPreventive) 40 else 20)
        ex1Id = intent.getStringExtra(EXTRA_EX1_ID)
        ex1Name = intent.getStringExtra(EXTRA_EX1_NAME)
        ex2Id = intent.getStringExtra(EXTRA_EX2_ID)
        ex2Name = intent.getStringExtra(EXTRA_EX2_NAME)
    }

    private fun setupUI() {
        // 1. Time Badge
        binding.tvTimeElapsedBadge.text = "⏰ $intervalMinutes MINS ALREADY!"

        // 2. Type-specific messaging
        val isExercise = alarmType == AlarmCycleService.ALARM_TYPE_EXERCISE || alarmType == "SNOOZE_EXERCISE"

        if (isExercise) {
            binding.tvBigEmojiIcon.text = "🧘‍♂️"
            binding.tvAlertTitle.text = "Time to Decompress! ✨"
            binding.tvAlertSubtitle.text = "You've been in one posture for $intervalMinutes minutes.\nRelieve cervical & lumbar pressure with 2 quick posture resets! 💪🌿"
            binding.btnStartRoutine.text = "👉 Touch to Start Exercises 🧘"
            binding.tvTouchHint.text = "Touch anywhere to silence alarm and begin exercise break"
        } else {
            binding.tvBigEmojiIcon.text = "🚶‍♂️"
            binding.tvAlertTitle.text = "Time for a Walking Break! ✨"
            binding.tvAlertSubtitle.text = "Stand up and walk for 5 minutes. Get blood flowing and decompress your spine! 🚶‍♀️💫"
            binding.btnStartRoutine.text = "👉 Touch to Start Walking 🚶"
            binding.tvTouchHint.text = "Touch anywhere to silence alarm and begin walk timer"
        }
    }

    private fun setupListeners() {
        // Touching either the prominent button or anywhere on screen starts the exercise/walk activity
        val onProceed: () -> Unit = {
            AlarmSoundManager.stop(this)

            val isExercise = alarmType == AlarmCycleService.ALARM_TYPE_EXERCISE || alarmType == "SNOOZE_EXERCISE"
            if (isExercise) {
                val exerciseIntent = Intent(this, ExerciseAlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(ExerciseAlarmActivity.EXTRA_EX1_ID, ex1Id)
                    putExtra(ExerciseAlarmActivity.EXTRA_EX1_NAME, ex1Name)
                    putExtra(ExerciseAlarmActivity.EXTRA_EX2_ID, ex2Id)
                    putExtra(ExerciseAlarmActivity.EXTRA_EX2_NAME, ex2Name)
                }
                startActivity(exerciseIntent)
            } else {
                val walkIntent = Intent(this, WalkAlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(walkIntent)
            }
            finish()
        }

        binding.btnStartRoutine.setOnClickListener { onProceed() }
        binding.rootAlarmAlert.setOnClickListener { onProceed() }

        binding.btnSnoozeAlert.setOnClickListener {
            AlarmSoundManager.stop(this)
            val currentType = if (alarmType.contains("WALK")) AlarmCycleService.ALARM_TYPE_WALK else AlarmCycleService.ALARM_TYPE_EXERCISE
            AlarmCycleService.snooze(this, currentType)
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AlarmSoundManager.stop(this)
    }

    companion object {
        const val EXTRA_ALARM_TYPE = "EXTRA_ALARM_TYPE"
        const val EXTRA_IS_PREVENTIVE = "EXTRA_IS_PREVENTIVE"
        const val EXTRA_INTERVAL_MINUTES = "EXTRA_INTERVAL_MINUTES"
        const val EXTRA_EX1_ID = "EXTRA_EX1_ID"
        const val EXTRA_EX1_NAME = "EXTRA_EX1_NAME"
        const val EXTRA_EX2_ID = "EXTRA_EX2_ID"
        const val EXTRA_EX2_NAME = "EXTRA_EX2_NAME"
    }
}
