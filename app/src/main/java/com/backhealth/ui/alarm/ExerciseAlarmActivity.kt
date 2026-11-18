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

    private var currentExercise1: com.backhealth.model.Exercise? = null
    private var currentExercise2: com.backhealth.model.Exercise? = null
    private var selectedTab = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockscreen()
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Clear outside status-bar alarm notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(AlarmReceiver.NOTIFICATION_ID_EXERCISE)

        // Acknowledge alarm with cycle service to cancel pending auto-snooze loop
        AlarmCycleService.alarmAcknowledged(this)

        setupVideoTabs()
        extractExercises()
        playAlert()
        startMicroBreakTimer()
        setupButtons()
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(AlarmReceiver.NOTIFICATION_ID_EXERCISE)
        AlarmCycleService.alarmAcknowledged(this)
        extractExercises()
        playAlert()
        startMicroBreakTimer()
    }

    private fun extractExercises() {
        val extraEx1Id = intent.getStringExtra(EXTRA_EX1_ID)
        val extraEx2Id = intent.getStringExtra(EXTRA_EX2_ID)

        val db = (application as BackHealthApp).database
        lifecycleScope.launch {
            val (e1, e2) = if (extraEx1Id != null && extraEx2Id != null) {
                val dbE1 = db.exerciseDao().getById(extraEx1Id) ?: ExercisePicker.getExerciseById(extraEx1Id)
                val dbE2 = db.exerciseDao().getById(extraEx2Id) ?: ExercisePicker.getExerciseById(extraEx2Id)
                if (dbE1 != null && dbE2 != null) {
                    Pair(dbE1, dbE2)
                } else {
                    ExercisePicker.pickExercisesForMicroBreak(db.exerciseDao())
                }
            } else {
                ExercisePicker.pickExercisesForMicroBreak(db.exerciseDao())
            }

            populateExercise1(e1)
            populateExercise2(e2)
        }
    }

    private fun setupVideoTabs() {
        binding.btnTabEx1.setOnClickListener {
            selectedTab = 1
            highlightTab(1)
            play3DVideo(currentExercise1)
        }

        binding.btnTabEx2.setOnClickListener {
            selectedTab = 2
            highlightTab(2)
            play3DVideo(currentExercise2)
        }
    }

    private fun highlightTab(tabIndex: Int) {
        if (tabIndex == 1) {
            binding.btnTabEx1.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(this, com.backhealth.R.color.neon_mint)
            binding.btnTabEx1.setTextColor(androidx.core.content.ContextCompat.getColor(this, com.backhealth.R.color.bg_obsidian))
            binding.btnTabEx2.backgroundTintList = null
            binding.btnTabEx2.setTextColor(androidx.core.content.ContextCompat.getColor(this, com.backhealth.R.color.text_primary))
        } else {
            binding.btnTabEx2.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(this, com.backhealth.R.color.neon_cyan)
            binding.btnTabEx2.setTextColor(androidx.core.content.ContextCompat.getColor(this, com.backhealth.R.color.bg_obsidian))
            binding.btnTabEx1.backgroundTintList = null
            binding.btnTabEx1.setTextColor(androidx.core.content.ContextCompat.getColor(this, com.backhealth.R.color.text_primary))
        }
    }

    private fun play3DVideo(exercise: com.backhealth.model.Exercise?) {
        // Videos commented out / disabled for Phase 1 as per user request
        binding.cardVideoContainer.visibility = android.view.View.GONE
        try {
            binding.videoView3D.stopPlayback()
            binding.videoView3D.visibility = android.view.View.GONE
        } catch (_: Exception) {}
        /*
        if (exercise == null) return
        binding.tv3DBadge.text = "🎬 FORM DEMO: ${exercise.name.uppercase()}"
        val videoUrl = exercise.videoUrl.ifEmpty {
            com.backhealth.utils.ExercisePicker.getExerciseById(exercise.id)?.videoUrl.orEmpty()
        }.ifEmpty { null }
        if (videoUrl != null) {
            try {
                binding.ivVideoPlaceholder.visibility = android.view.View.GONE
                binding.videoView3D.visibility = android.view.View.VISIBLE
                binding.videoView3D.setVideoURI(android.net.Uri.parse(videoUrl))
                binding.videoView3D.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    binding.videoView3D.start()
                }
                binding.videoView3D.setOnErrorListener { _, _, _ ->
                    binding.videoView3D.visibility = android.view.View.GONE
                    binding.ivVideoPlaceholder.visibility = android.view.View.VISIBLE
                    true
                }
            } catch (e: Exception) {
                binding.videoView3D.visibility = android.view.View.GONE
                binding.ivVideoPlaceholder.visibility = android.view.View.VISIBLE
            }
        } else {
            binding.videoView3D.visibility = android.view.View.GONE
            binding.ivVideoPlaceholder.visibility = android.view.View.VISIBLE
        }
        */
    }

    private fun populateExercise1(e: com.backhealth.model.Exercise) {
        currentExercise1 = e
        ex1Id = e.id
        ex1Name = e.name
        binding.tvEx1Name.text = e.name
        binding.tvEx1Duration.text = "⏱ ${e.durationValue} ${e.durationUnit}"
        binding.tvEx1Cue.text = e.coachingCue
        binding.btnTabEx1.text = "1. ${e.name}"
        if (e.warning.isNotEmpty()) {
            binding.tvEx1Warning.text = "⚠️ ${e.warning}"
        }
        if (selectedTab == 1) {
            play3DVideo(e)
        }
    }

    private fun populateExercise2(e: com.backhealth.model.Exercise) {
        currentExercise2 = e
        ex2Id = e.id
        ex2Name = e.name
        binding.tvEx2Name.text = e.name
        binding.tvEx2Duration.text = "⏱ ${e.durationValue} ${e.durationUnit}"
        binding.tvEx2Cue.text = e.coachingCue
        binding.btnTabEx2.text = "2. ${e.name}"
        if (e.warning.isNotEmpty()) {
            binding.tvEx2Warning.text = "⚠️ ${e.warning}"
        }
        if (selectedTab == 2) {
            play3DVideo(e)
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

            // Automatically silence after 3 seconds so user is not disturbed while doing exercise
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopAlert()
            }, 3000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // Stop ringing the instant user touches or scrolls on the exercise screen
        stopAlert()
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

    override fun onPause() {
        super.onPause()
        stopAlert()
        try {
            binding.videoView3D.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlert()
        countDownTimer?.cancel()
        try {
            binding.videoView3D.stopPlayback()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val EXTRA_EX1_ID = "EXTRA_EX1_ID"
        const val EXTRA_EX1_NAME = "EXTRA_EX1_NAME"
        const val EXTRA_EX2_ID = "EXTRA_EX2_ID"
        const val EXTRA_EX2_NAME = "EXTRA_EX2_NAME"
    }
}
