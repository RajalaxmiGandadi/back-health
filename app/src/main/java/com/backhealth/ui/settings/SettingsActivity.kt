package com.backhealth.ui.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.backhealth.BackHealthApp
import com.backhealth.alarm.AlarmCycleService
import com.backhealth.databinding.ActivitySettingsBinding
import com.backhealth.model.UserPrefs
import com.backhealth.repository.ExerciseRepository
import com.backhealth.repository.UserPrefsRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var userPrefsRepo: UserPrefsRepository
    private lateinit var exerciseRepo: ExerciseRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = (application as BackHealthApp).database
        userPrefsRepo = UserPrefsRepository(db.userPrefsDao())
        exerciseRepo = (application as BackHealthApp).exerciseRepository

        binding.toolbarSettings.setNavigationOnClickListener { finish() }

        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val prefs = userPrefsRepo.getPrefs()
            binding.btnSettingWakeTime.text = "Wake: ${prefs.wakeTime}"
            binding.btnSettingBedTime.text = "Bed: ${prefs.bedTime}"
            binding.switchSound.isChecked = prefs.soundEnabled
            binding.switchVibration.isChecked = prefs.vibrationEnabled
            updateBreakTimesUI()
        }
    }

    private fun updateBreakTimesUI() {
        lifecycleScope.launch {
            val prefs = userPrefsRepo.getPrefs()
            val isPreventive = prefs.userType == UserPrefs.USER_TYPE_PREVENTIVE
            val state = AlarmCycleService.cycleState.value
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val now = System.currentTimeMillis()

            if (state.isPaused) {
                if (isPreventive) {
                    binding.tvNextSmallBreak.visibility = View.GONE
                    binding.tvNextBigBreak.text = "next walk break: Paused"
                } else {
                    binding.tvNextSmallBreak.visibility = View.VISIBLE
                    binding.tvNextSmallBreak.text = "next small break: Paused"
                    binding.tvNextBigBreak.text = "next big break: Paused"
                }
                return@launch
            }

            if (isPreventive) {
                // Preventive Mode: No small break! Walk every 40 mins
                binding.tvNextSmallBreak.visibility = View.GONE
                val walkEpoch = if (state.nextAlarmEpochMillis > 0L) {
                    state.nextAlarmEpochMillis
                } else {
                    now + (AlarmCycleService.INTERVAL_PREVENTIVE_WALK_MINUTES * 60 * 1000L)
                }
                val walkDiffMin = maxOf(0L, (walkEpoch - now) / 60000)
                binding.tvNextBigBreak.text = "next walk break: ${sdf.format(Date(walkEpoch))} (in ${walkDiffMin}m)"
                binding.tvCycleDescription.text = "💡 Preventive Mode: Walk for 5 mins after every 40 mins.\nNo 20-min exercise alarm required."
            } else {
                // Disc Patient: Alternating 20m cycle
                binding.tvNextSmallBreak.visibility = View.VISIBLE
                val isSmallNext = state.nextAlarmType == AlarmCycleService.ALARM_TYPE_EXERCISE
                val smallEpoch: Long
                val bigEpoch: Long

                if (isSmallNext) {
                    smallEpoch = if (state.nextAlarmEpochMillis > 0L) state.nextAlarmEpochMillis else now + (AlarmCycleService.INTERVAL_EXERCISE_MINUTES * 60 * 1000L)
                    bigEpoch = smallEpoch + (AlarmCycleService.INTERVAL_WALK_MINUTES * 60 * 1000L)
                } else {
                    bigEpoch = if (state.nextAlarmEpochMillis > 0L) state.nextAlarmEpochMillis else now + (AlarmCycleService.INTERVAL_WALK_MINUTES * 60 * 1000L)
                    smallEpoch = bigEpoch + (AlarmCycleService.INTERVAL_EXERCISE_MINUTES * 60 * 1000L)
                }

                val smallDiffMin = maxOf(0L, (smallEpoch - now) / 60000)
                val bigDiffMin = maxOf(0L, (bigEpoch - now) / 60000)

                binding.tvNextSmallBreak.text = "next small break: ${sdf.format(Date(smallEpoch))} (in ${smallDiffMin}m)"
                binding.tvNextBigBreak.text = "next big break: ${sdf.format(Date(bigEpoch))} (in ${bigDiffMin}m)"
                binding.tvCycleDescription.text = "💡 All exercises are for small break.\nWalking is for big break."
            }
        }
    }

    private fun setupListeners() {
        binding.btnSettingWakeTime.setOnClickListener {
            lifecycleScope.launch {
                val current = userPrefsRepo.getPrefs().wakeTime.split(":")
                val h = current.getOrNull(0)?.toIntOrNull() ?: 7
                val m = current.getOrNull(1)?.toIntOrNull() ?: 0

                TimePickerDialog(this@SettingsActivity, { _, hourOfDay, minute ->
                    val newTime = String.format("%02d:%02d", hourOfDay, minute)
                    binding.btnSettingWakeTime.text = "Wake: $newTime"
                    lifecycleScope.launch {
                        val prefs = userPrefsRepo.getPrefs().copy(wakeTime = newTime)
                        userPrefsRepo.savePrefs(prefs)
                    }
                }, h, m, false).show()
            }
        }

        binding.btnSettingBedTime.setOnClickListener {
            lifecycleScope.launch {
                val current = userPrefsRepo.getPrefs().bedTime.split(":")
                val h = current.getOrNull(0)?.toIntOrNull() ?: 22
                val m = current.getOrNull(1)?.toIntOrNull() ?: 0

                TimePickerDialog(this@SettingsActivity, { _, hourOfDay, minute ->
                    val newTime = String.format("%02d:%02d", hourOfDay, minute)
                    binding.btnSettingBedTime.text = "Bed: $newTime"
                    lifecycleScope.launch {
                        val prefs = userPrefsRepo.getPrefs().copy(bedTime = newTime)
                        userPrefsRepo.savePrefs(prefs)
                    }
                }, h, m, false).show()
            }
        }

        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                val prefs = userPrefsRepo.getPrefs().copy(soundEnabled = isChecked)
                userPrefsRepo.savePrefs(prefs)
            }
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                val prefs = userPrefsRepo.getPrefs().copy(vibrationEnabled = isChecked)
                userPrefsRepo.savePrefs(prefs)
            }
        }

        binding.btnResetExercises.setOnClickListener {
            lifecycleScope.launch {
                exerciseRepo.resetAllExercises()
                Toast.makeText(this@SettingsActivity, "All exercises re-enabled in routine", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
