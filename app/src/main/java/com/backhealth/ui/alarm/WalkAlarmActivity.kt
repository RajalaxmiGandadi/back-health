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
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.backhealth.BackHealthApp
import com.backhealth.alarm.AlarmCycleService
import com.backhealth.alarm.AlarmReceiver
import com.backhealth.databinding.ActivityWalkAlarmBinding
import com.backhealth.model.UserPrefs
import com.backhealth.repository.ProgressRepository
import kotlinx.coroutines.launch

class WalkAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkAlarmBinding
    private var countDownTimer: CountDownTimer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private var walkDurationSec = 300 // default 5 mins (disc patient)
    private var isPreventive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockscreen()
        super.onCreate(savedInstanceState)
        binding = ActivityWalkAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Clear outside status-bar alarm notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(AlarmReceiver.NOTIFICATION_ID_WALK)

        // Acknowledge alarm with cycle service to cancel pending auto-snooze loop
        AlarmCycleService.alarmAcknowledged(this)

        loadUserTypeSettings()
        playAlert()
        setupButtons()
    }

    private fun loadUserTypeSettings() {
        val db = (application as BackHealthApp).database
        lifecycleScope.launch {
            val prefs = db.userPrefsDao().getPrefs()
            isPreventive = prefs?.userType == UserPrefs.USER_TYPE_PREVENTIVE

            if (isPreventive) {
                // People without back issues: 5-minute walk break every 40 mins
                walkDurationSec = 300
                binding.tvWalkTitle.text = "🚶 Walk Break"
                binding.tvWalkSubtitle.text = "Take a 5-minute walking break. Get your blood flowing, clear your mind, and keep your posture active."
                binding.tvWalkTimerText.text = "05:00"
                binding.walkProgressBar.max = 300
                binding.walkProgressBar.progress = 300
                binding.btnFinishWalkEarly.text = "✅ Finished Walk (Next Walk in 40m)"
            } else {
                // People with lumbar disc issues: 5-min walk decompression
                walkDurationSec = 300
                binding.tvWalkTitle.text = "🚶 Big Break"
                binding.tvWalkSubtitle.text = "Get up from your chair, stretch your legs, and walk for 5 minutes. Decompress your lumbar spine."
                binding.tvWalkTimerText.text = "05:00"
                binding.walkProgressBar.max = 300
                binding.walkProgressBar.progress = 300
                binding.btnFinishWalkEarly.text = "✅ Finished Walk (Next Posture Break)"
            }
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(AlarmReceiver.NOTIFICATION_ID_WALK)
        AlarmCycleService.alarmAcknowledged(this)
        playAlert()
    }

    private fun playAlert() {
        com.backhealth.utils.AlarmSoundManager.play(this)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        stopAlert()
    }

    override fun onPause() {
        super.onPause()
        stopAlert()
    }

    private fun stopAlert() {
        com.backhealth.utils.AlarmSoundManager.stop(this)
    }

    private fun setupButtons() {
        binding.btnStartWalking.setOnClickListener {
            stopAlert()
            startWalkCountdown()
        }

        binding.btnSnoozeWalk.setOnClickListener {
            stopAlert()
            AlarmCycleService.snooze(this, AlarmCycleService.ALARM_TYPE_WALK)
            finish()
        }

        binding.btnFinishWalkEarly.setOnClickListener {
            completeWalk()
        }
    }

    private fun startWalkCountdown() {
        binding.layoutWalkInitialButtons.visibility = View.GONE
        binding.cardWalkTimer.visibility = View.VISIBLE
        binding.btnFinishWalkEarly.visibility = View.VISIBLE

        val totalMillis = walkDurationSec * 1000L
        countDownTimer = object : CountDownTimer(totalMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSec = millisUntilFinished / 1000
                val mins = totalSec / 60
                val secs = totalSec % 60
                binding.tvWalkTimerText.text = String.format("%02d:%02d", mins, secs)
                binding.walkProgressBar.progress = totalSec.toInt()
            }

            override fun onFinish() {
                completeWalk()
            }
        }.start()
    }

    private fun completeWalk() {
        countDownTimer?.cancel()
        val db = (application as BackHealthApp).database
        val progressRepo = ProgressRepository(db.exerciseLogDao(), db.walkLogDao())
        val minutesToLog = 5

        lifecycleScope.launch {
            progressRepo.recordWalkCompleted(minutesToLog)
            // Report completed -> AlarmCycleService schedules next alarm according to userType!
            AlarmCycleService.taskCompleted(this@WalkAlarmActivity, AlarmCycleService.ALARM_TYPE_WALK)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlert()
        countDownTimer?.cancel()
    }
}
