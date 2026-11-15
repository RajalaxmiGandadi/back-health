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
import java.util.Calendar

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

        setupTimePickers()
        setupSubmitButton()
    }

    private fun setupTimePickers() {
        binding.btnWakeTime.setOnClickListener {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                wakeHour = hourOfDay
                wakeMin = minute
                binding.btnWakeTime.text = String.format("Wake: %02d:%02d", hourOfDay, minute)
            }, wakeHour, wakeMin, false).show()
        }

        binding.btnBedTime.setOnClickListener {
            TimePickerDialog(this, { _, hourOfDay, minute ->
                bedHour = hourOfDay
                bedMin = minute
                binding.btnBedTime.text = String.format("Bed: %02d:%02d", hourOfDay, minute)
            }, bedHour, bedMin, false).show()
        }
    }

    private fun setupSubmitButton() {
        binding.btnGetStarted.setOnClickListener {
            val userType = if (binding.rbDiscPatient.isChecked) "DISC_PATIENT" else "PREVENTIVE"
            val wakeTimeStr = String.format("%02d:%02d", wakeHour, wakeMin)
            val bedTimeStr = String.format("%02d:%02d", bedHour, bedMin)

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

                // Start the background alarm cycle!
                AlarmCycleService.start(this@OnboardingActivity)

                startActivity(Intent(this@OnboardingActivity, HomeActivity::class.java))
                finish()
            }
        }
    }
}
