package com.backhealth.ui.onboarding

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.backhealth.BackHealthApp
import com.backhealth.alarm.AlarmCycleService
import com.backhealth.databinding.ActivityOnboardingBinding
import com.backhealth.model.UserPrefs
import com.backhealth.repository.UserPrefsRepository
import com.backhealth.ui.home.HomeActivity
import kotlinx.coroutines.launch
import java.util.Locale

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var wakeHour = 7
    private var wakeMin = 0
    private var bedHour = 22
    private var bedMin = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPersonaSelection()
        setupTimePickers()
        setupSubmitButton()
    }

    private fun setupPersonaSelection() {
        // Tapping either the card or the radio button selects that persona
        binding.cardDiscPatient.setOnClickListener {
            binding.rbDiscPatient.isChecked = true
            binding.rbPreventive.isChecked = false
        }

        binding.cardPreventive.setOnClickListener {
            binding.rbPreventive.isChecked = true
            binding.rbDiscPatient.isChecked = false
        }

        binding.rbDiscPatient.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.rbPreventive.isChecked = false
            }
        }

        binding.rbPreventive.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.rbDiscPatient.isChecked = false
            }
        }
    }

    private fun setupTimePickers() {
        binding.btnWakeTime.setOnClickListener {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                wakeHour = hourOfDay
                wakeMin = minute
                binding.btnWakeTime.text = "⏰ Wake-Up: ${formatTime12Hour(wakeHour, wakeMin)} (Tap to change)"
            }, wakeHour, wakeMin, false).show()
        }

        binding.btnBedTime.setOnClickListener {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                bedHour = hourOfDay
                bedMin = minute
                binding.btnBedTime.text = "🌙 Bed Time: ${formatTime12Hour(bedHour, bedMin)} (Tap to change)"
            }, bedHour, bedMin, false).show()
        }
    }

    private fun setupSubmitButton() {
        binding.btnGetStarted.setOnClickListener {
            val userType = if (binding.rbDiscPatient.isChecked) {
                UserPrefs.USER_TYPE_DISC_PATIENT
            } else {
                UserPrefs.USER_TYPE_PREVENTIVE
            }

            val wakeTimeStr = String.format(Locale.US, "%02d:%02d", wakeHour, wakeMin)
            val bedTimeStr = String.format(Locale.US, "%02d:%02d", bedHour, bedMin)

            // 1. Immediately persist to SharedPreferences for synchronous instant startup checks
            val sp = getSharedPreferences("spinehealth_user_prefs", android.content.Context.MODE_PRIVATE)
            sp.edit()
                .putInt("setup_version", 2)
                .putBoolean("onboarding_complete", true)
                .putString("user_type", userType)
                .putString("wake_time", wakeTimeStr)
                .putString("bed_time", bedTimeStr)
                .apply()

            // 2. Immediately start timer and schedule the alarm cycle synchronously (0ms delay)
            AlarmCycleService.startCycleImmediate(this@OnboardingActivity, userType, wakeTimeStr, bedTimeStr)

            // 3. Persist to Room database asynchronously
            val db = (application as BackHealthApp).database
            val userPrefsRepo = UserPrefsRepository(db.userPrefsDao())

            lifecycleScope.launch {
                val prefs = UserPrefs(
                    id = 1,
                    userType = userType,
                    wakeTime = wakeTimeStr,
                    bedTime = bedTimeStr,
                    isOnboardingComplete = true,
                    isCyclePaused = false
                )
                userPrefsRepo.savePrefs(prefs)

                val intent = Intent(this@OnboardingActivity, HomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun formatTime12Hour(hour: Int, minute: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val displayHour = when (hour) {
            0 -> 12
            in 1..12 -> hour
            else -> hour - 12
        }
        return String.format(Locale.US, "%02d:%02d %s", displayHour, minute, period)
    }
}
