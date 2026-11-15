package com.backhealth.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.backhealth.BackHealthApp
import com.backhealth.alarm.AlarmCycleService
import com.backhealth.alarm.AlarmReceiver
import com.backhealth.databinding.ActivityExerciseAlarmBinding
import com.backhealth.repository.ProgressRepository
import com.backhealth.utils.ExercisePicker
import kotlinx.coroutines.launch

class ExerciseAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExerciseAlarmBinding
    private var countDownTimer: CountDownTimer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var ex1Id: String = "EX_L1"
    private var ex1Name: String = "Slow Standing March"
    private var ex2Id: String = "EX_U1"
    private var ex2Name: String = "Standing Chin Tucks"

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockscreen()
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        extractExercises()
        playAlert()
        startMicroBreakTimer()
        setupButtons()
    }

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

    private fun extractExercises() {
        val extraEx1Id = intent.getStringExtra(EXTRA_EX1_ID)
        val extraEx2Id = intent.getStringExtra(EXTRA_EX2_ID)

        val db = (application as BackHealthApp).database
        lifecycleScope.launch {
            if (extraEx1Id != null && extraEx2Id != null) {
                val e1 = db.exerciseDao().getById(extraEx1Id)
                val e2 = db.exerciseDao().getById(extraEx2Id)
                if (e1 != null) populateExercise1(e1)
                if (e2 != null) populateExercise2(e2)
            } else {
                val (e1, e2) = ExercisePicker.pickExercisesForMicroBreak(db.exerciseDao())
                e1?.let { populateExercise1(it) }
                e2?.let { populateExercise2(it) }
            }
        }
    }

    private fun populateExercise1(e: com.backhealth.model.Exercise) {
        ex1Id = e.id
        ex1Name = e.name
        binding.tvEx1Name.text = e.name
        binding.tvEx1Duration.text = "⏱ ${e.durationValue} ${e.durationUnit}"
        binding.tvEx1Cue.text = e.coachingCue
        if (e.warning.isNotEmpty()) {
            binding.tvEx1Warning.text = "⚠️ ${e.warning}"
        }
    }

    private fun populateExercise2(e: com.backhealth.model.Exercise) {
        ex2Id = e.id
        ex2Name = e.name
        binding.tvEx2Name.text = e.name
        binding.tvEx2Duration.text = "⏱ ${e.durationValue} ${e.durationUnit}"
        binding.tvEx2Cue.text = e.coachingCue
        if (e.warning.isNotEmpty()) {
            binding.tvEx2Warning.text = "⚠️ ${e.warning}"
        }
    }

    private fun playAlert() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            ringtone?.play()

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 500, 300, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlert() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startMicroBreakTimer() {
        val totalMillis = 150 * 1000L // 2.5 minutes (150 seconds)
        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSec = millisUntilFinished / 1000
                val mins = totalSec / 60
                val secs = totalSec % 60
                binding.tvTimerRemaining.text = String.format("%02d:%02d", mins, secs)
                binding.progressBar.progress = totalSec.toInt()
            }

            override fun onFinish() {
                binding.tvTimerRemaining.text = "00:00"
                binding.progressBar.progress = 0
            }
        }.start()
    }

    private fun setupButtons() {
        binding.btnDone.setOnClickListener {
            stopAlert()
            countDownTimer?.cancel()

            // Record completed in Room DB
            val db = (application as BackHealthApp).database
            val progressRepo = ProgressRepository(db.exerciseLogDao(), db.walkLogDao())
            lifecycleScope.launch {
                progressRepo.recordExerciseCompleted(ex1Id, ex1Name, ex2Id, ex2Name)
                // Notify AlarmCycleService that exercise is complete -> start 40m walk countdown!
                AlarmCycleService.taskCompleted(this@ExerciseAlarmActivity, AlarmCycleService.ALARM_TYPE_EXERCISE)
                finish()
            }
        }

        binding.btnSnooze.setOnClickListener {
            stopAlert()
            countDownTimer?.cancel()
            // Snooze re-triggers in 2 minutes
            AlarmCycleService.snooze(this, AlarmCycleService.ALARM_TYPE_EXERCISE)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlert()
        countDownTimer?.cancel()
    }

    companion object {
        const val EXTRA_EX1_ID = "EXTRA_EX1_ID"
        const val EXTRA_EX1_NAME = "EXTRA_EX1_NAME"
        const val EXTRA_EX2_ID = "EXTRA_EX2_ID"
        const val EXTRA_EX2_NAME = "EXTRA_EX2_NAME"
    }
}
