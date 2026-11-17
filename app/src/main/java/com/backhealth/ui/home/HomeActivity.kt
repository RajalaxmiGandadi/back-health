package com.backhealth.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.backhealth.BackHealthApp
import com.backhealth.R
import com.backhealth.alarm.AlarmCycleService
import com.backhealth.databinding.ActivityHomeBinding
import com.backhealth.databinding.BottomSheetRestBinding
import com.backhealth.repository.ProgressRepository
import com.backhealth.repository.UserPrefsRepository
import com.backhealth.ui.library.ExerciseLibraryActivity
import com.backhealth.ui.onboarding.OnboardingActivity
import com.backhealth.ui.progress.ProgressActivity
import com.backhealth.ui.settings.SettingsActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var viewModel: HomeViewModel
    private var isPreventive: Boolean = false
    private var currentPrefs: com.backhealth.model.UserPrefs? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission response handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = (application as BackHealthApp).database
        val progressRepo = ProgressRepository(db.exerciseLogDao(), db.walkLogDao())
        val userPrefsRepo = UserPrefsRepository(db.userPrefsDao())
        viewModel = HomeViewModel(progressRepo, userPrefsRepo)

        checkOnboardingStatus(userPrefsRepo)
        requestRequiredPermissions()
        setupClickListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadStreak()
    }

    private fun checkOnboardingStatus(userPrefsRepo: UserPrefsRepository) {
        lifecycleScope.launch {
            val prefs = userPrefsRepo.getPrefs()
            if (!prefs.isOnboardingComplete) {
                startActivity(Intent(this@HomeActivity, OnboardingActivity::class.java))
                finish()
            } else {
                // Onboarding complete - ensure AlarmCycleService is running
                if (!prefs.isCyclePaused) {
                    AlarmCycleService.start(this@HomeActivity)
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupClickListeners() {
        binding.cardStreak.setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnLibrary.setOnClickListener {
            startActivity(Intent(this, ExerciseLibraryActivity::class.java))
        }

        binding.btnViewProgress.setOnClickListener {
            startActivity(Intent(this, ProgressActivity::class.java))
        }

        binding.btnTakeRest.setOnClickListener {
            showRestBottomSheet()
        }

        binding.btnPauseResume.setOnClickListener {
            val state = viewModel.cycleState.value
            if (state.isPaused) {
                AlarmCycleService.resumeCycle(this)
                viewModel.toggleCyclePause(true)
            } else {
                AlarmCycleService.pauseCycle(this)
                viewModel.toggleCyclePause(false)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.countdownText.collectLatest { timeText ->
                        binding.tvCountdown.text = timeText
                        val state = viewModel.cycleState.value
                        if (state.nextAlarmEpochMillis > 0L) {
                            val remainingSec = (maxOf(0L, state.nextAlarmEpochMillis - System.currentTimeMillis()) / 1000).toInt()
                            val totalSec = if (isPreventive) {
                                AlarmCycleService.INTERVAL_PREVENTIVE_WALK_MINUTES * 60
                            } else if (state.nextAlarmType == AlarmCycleService.ALARM_TYPE_EXERCISE) {
                                AlarmCycleService.INTERVAL_EXERCISE_MINUTES * 60
                            } else {
                                AlarmCycleService.INTERVAL_WALK_MINUTES * 60
                            }
                            binding.circularTimerProgress.max = maxOf(1, totalSec)
                            binding.circularTimerProgress.setProgressCompat(minOf(totalSec, remainingSec), true)
                        } else if (state.isResting && state.restEndEpochMillis > 0L) {
                            val remainingSec = (maxOf(0L, state.restEndEpochMillis - System.currentTimeMillis()) / 1000).toInt()
                            binding.circularTimerProgress.setProgressCompat(remainingSec, true)
                        }
                    }
                }

                launch {
                    viewModel.cycleState.collectLatest { state ->
                        updateCycleUI(state)
                    }
                }

                launch {
                    viewModel.todayExercisesCount.collectLatest { count ->
                        binding.tvExercisesDone.text = count.toString()
                    }
                }

                launch {
                    viewModel.todayWalksCount.collectLatest { count ->
                        binding.tvWalksDone.text = count.toString()
                    }
                }

                launch {
                    viewModel.streak.collectLatest { streakCount ->
                        binding.tvStreakCount.text = streakCount.toString()
                    }
                }

                launch {
                    viewModel.userPrefs.collectLatest { prefs ->
                        currentPrefs = prefs
                        isPreventive = prefs?.userType == com.backhealth.model.UserPrefs.USER_TYPE_PREVENTIVE
                        val focusLabel = if (isPreventive) {
                            "Focus: Preventive Care (Walk Every 40m)"
                        } else {
                            "Focus: Lumbar Disc Recovery (L4-L5/L5-S1)"
                        }
                        binding.tvUserTypeBadge.text = focusLabel
                        binding.cardSmallBreaksDone.visibility = if (isPreventive) View.GONE else View.VISIBLE
                        updateCycleUI(viewModel.cycleState.value)
                    }
                }
            }
        }
    }

    private fun updateCycleUI(state: AlarmCycleService.CycleState) {
        if (state.isPaused) {
            binding.tvNextAlarmLabel.text = "CYCLE PAUSED"
            binding.tvNextAlarmType.text = "⏸ Paused"
            binding.tvCycleState.text = "Tap resume below"
            binding.btnPauseResume.text = "▶ Resume Day"
            binding.btnTakeRest.isEnabled = false
        } else if (state.isResting) {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(state.restEndEpochMillis))
            binding.tvNextAlarmLabel.text = "RESTING UNTIL $timeStr"
            binding.tvNextAlarmType.text = "😴 Mid-Day Rest"
            binding.tvCycleState.text = "Alarms muted"
            binding.btnTakeRest.text = "⏰ Rest Active (Tap to Change)"
            binding.btnPauseResume.text = "⏸ Pause Day"
            binding.btnTakeRest.isEnabled = true
        } else {
            val wakeTime = currentPrefs?.wakeTime ?: "07:00"
            val bedTime = currentPrefs?.bedTime ?: "22:00"
            val isAwake = com.backhealth.alarm.SleepWindowChecker.isAwakeTime(wakeTime, bedTime)

            if (!isAwake) {
                binding.tvNextAlarmLabel.text = "SLEEP WINDOW ACTIVE"
                binding.tvNextAlarmType.text = "🌙 Sleeping (Wake: $wakeTime)"
                binding.tvCycleState.text = "Alarms muted until morning"
                binding.circularTimerProgress.setIndicatorColor(ContextCompat.getColor(this, R.color.card_border))
                binding.btnTakeRest.isEnabled = false
            } else if (isPreventive) {
                binding.tvNextAlarmLabel.text = "NEXT WALK BREAK IN"
                val typeTitle = "🚶 Walk Break (2–3 min)"
                val typeBadge = if (state.isSnooze) "⏰ Snoozed: $typeTitle" else typeTitle

                binding.tvNextAlarmType.text = typeBadge
                binding.tvCycleState.text = if (state.isSnooze) "Re-alerting in 2m" else "Cycle Active (Every 40m)"
                binding.circularTimerProgress.setIndicatorColor(ContextCompat.getColor(this, R.color.neon_cyan))
                binding.tvNextAlarmType.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue_light)
                binding.tvNextAlarmType.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
                binding.btnTakeRest.isEnabled = true
            } else {
                binding.tvNextAlarmLabel.text = "NEXT BREAK IN"
                val isExercise = state.nextAlarmType == AlarmCycleService.ALARM_TYPE_EXERCISE
                val typeTitle = if (isExercise) "🧘 Small Break (Posture)" else "🚶 Big Break (Walking)"
                val typeBadge = if (state.isSnooze) "⏰ Snoozed: $typeTitle" else typeTitle

                binding.tvNextAlarmType.text = typeBadge
                binding.tvCycleState.text = if (state.isSnooze) "Re-alerting in 2m" else "Cycle Active"
                if (isExercise) {
                    binding.circularTimerProgress.setIndicatorColor(ContextCompat.getColor(this, R.color.neon_mint))
                    binding.tvNextAlarmType.backgroundTintList = ContextCompat.getColorStateList(this, R.color.teal_light)
                    binding.tvNextAlarmType.setTextColor(ContextCompat.getColor(this, R.color.neon_mint))
                } else {
                    binding.circularTimerProgress.setIndicatorColor(ContextCompat.getColor(this, R.color.neon_cyan))
                    binding.tvNextAlarmType.backgroundTintList = ContextCompat.getColorStateList(this, R.color.blue_light)
                    binding.tvNextAlarmType.setTextColor(ContextCompat.getColor(this, R.color.neon_cyan))
                }
                binding.btnTakeRest.isEnabled = true
            }
            binding.btnTakeRest.text = "😴 Take a Mid-Day Rest"
            binding.btnPauseResume.text = "⏸ Pause Day"
        }
    }

    private fun showRestBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetRestBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        val isResting = viewModel.cycleState.value.isResting
        sheetBinding.btnWakeUpEarly.visibility = if (isResting) View.VISIBLE else View.GONE

        sheetBinding.btnRest20.setOnClickListener {
            AlarmCycleService.startRest(this, 20)
            dialog.dismiss()
        }
        sheetBinding.btnRest30.setOnClickListener {
            AlarmCycleService.startRest(this, 30)
            dialog.dismiss()
        }
        sheetBinding.btnRest40.setOnClickListener {
            AlarmCycleService.startRest(this, 40)
            dialog.dismiss()
        }
        sheetBinding.btnRest60.setOnClickListener {
            AlarmCycleService.startRest(this, 60)
            dialog.dismiss()
        }
        sheetBinding.btnRest90.setOnClickListener {
            AlarmCycleService.startRest(this, 90)
            dialog.dismiss()
        }
        sheetBinding.btnWakeUpEarly.setOnClickListener {
            AlarmCycleService.cancelRest(this)
            dialog.dismiss()
        }

        dialog.show()
    }
}
