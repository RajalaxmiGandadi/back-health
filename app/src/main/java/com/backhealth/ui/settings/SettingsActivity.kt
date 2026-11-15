package com.backhealth.ui.settings

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.backhealth.BackHealthApp
import com.backhealth.databinding.ActivitySettingsBinding
import com.backhealth.repository.ExerciseRepository
import com.backhealth.repository.UserPrefsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

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
