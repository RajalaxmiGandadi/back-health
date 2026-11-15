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
import com.backhealth.databinding.ActivityWalkAlarmBinding
import com.backhealth.repository.ProgressRepository
import kotlinx.coroutines.launch

class WalkAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalkAlarmBinding
    private var countDownTimer: CountDownTimer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        showOverLockscreen()
        super.onCreate(savedInstanceState)
        binding = ActivityWalkAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        playAlert()
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

            val pattern = longArrayOf(0, 400, 200, 400)
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

        val totalMillis = 300 * 1000L // 5 minutes = 300s
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
        lifecycleScope.launch {
            progressRepo.recordWalkCompleted(5)
            // Walk finished -> Next starts 20-min exercise timer!
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
