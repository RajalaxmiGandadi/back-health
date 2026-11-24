package com.backhealth.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.backhealth.alarm.AlarmCycleService
import com.backhealth.model.UserPrefs
import com.backhealth.repository.ProgressRepository
import com.backhealth.repository.UserPrefsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(
    private val progressRepo: ProgressRepository,
    private val userPrefsRepo: UserPrefsRepository
) : ViewModel() {

    val cycleState: StateFlow<AlarmCycleService.CycleState> = AlarmCycleService.cycleState

    val todayExercisesCount: Flow<Int> = progressRepo.getTodayExercisesCount()
    val todayWalksCount: Flow<Int> = progressRepo.getTodayWalksCount()
    val userPrefs: Flow<UserPrefs?> = userPrefsRepo.getPrefsFlow()

    private val _streak = MutableStateFlow(1)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private val _countdownText = MutableStateFlow("--:--")
    val countdownText: StateFlow<String> = _countdownText.asStateFlow()

    init {
        loadStreak()
        startCountdownTicker()
    }

    fun loadStreak() {
        viewModelScope.launch {
            _streak.value = progressRepo.calculateCurrentStreak()
        }
    }

    fun toggleCyclePause(currentlyPaused: Boolean) {
        viewModelScope.launch {
            userPrefsRepo.setCyclePaused(!currentlyPaused)
        }
    }

    private fun startCountdownTicker() {
        viewModelScope.launch {
            while (true) {
                val state = cycleState.value
                val now = System.currentTimeMillis()
                if (state.isResting) {
                    val remainingMs = maxOf(0L, state.restEndEpochMillis - now)
                    _countdownText.value = formatDuration(remainingMs)
                } else if (state.nextAlarmEpochMillis > 0L) {
                    val remainingMs = maxOf(0L, state.nextAlarmEpochMillis - now)
                    _countdownText.value = formatDuration(remainingMs)
                } else {
                    // Fallback to persisted SharedPreferences immediately if in-memory state is still warming up
                    val sp = com.backhealth.BackHealthApp.instance.getSharedPreferences(
                        AlarmCycleService.PREFS_CYCLE,
                        android.content.Context.MODE_PRIVATE
                    )
                    val savedEpoch = sp.getLong(AlarmCycleService.KEY_NEXT_ALARM_EPOCH, 0L)
                    if (savedEpoch > now) {
                        _countdownText.value = formatDuration(savedEpoch - now)
                    } else {
                        _countdownText.value = "--:--"
                    }
                }
                delay(1000)
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSec = millis / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
