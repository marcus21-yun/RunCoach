package com.runcoach.app.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.app.data.db.RunningRecordDao
import com.runcoach.app.data.db.WeeklyGoal
import com.runcoach.app.data.db.WeeklyGoalDao
import com.runcoach.app.domain.AiGoalAdvisor
import com.runcoach.app.domain.AiSuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class AiGoalViewModel @Inject constructor(
    private val recordDao: RunningRecordDao,
    private val goalDao: WeeklyGoalDao,
    private val advisor: AiGoalAdvisor
) : ViewModel() {

    private val _suggestion = MutableStateFlow<AiSuggestion?>(null)
    val suggestion: StateFlow<AiSuggestion?> = _suggestion

    init { generateSuggestion() }

    private fun generateSuggestion() {
        viewModelScope.launch {
            val lastRecord = recordDao.getLastRecord() ?: return@launch
            val currentGoal = goalDao.getCurrentGoalOnce() ?: return@launch
            val halfMarathonDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365)
            _suggestion.value = advisor.suggestNextGoal(lastRecord, currentGoal, halfMarathonDate)
        }
    }

    fun acceptSuggestion() {
        viewModelScope.launch {
            val s = _suggestion.value ?: return@launch
            val weekStart = getThisWeekSunday()
            goalDao.insert(
                WeeklyGoal(
                    weekStart = weekStart,
                    targetKm = s.targetKm,
                    targetPace = s.targetPace,
                    speedupKm = s.speedupKm,
                    speedupPct = s.speedupPct,
                    hrAlertBpm = s.hrAlertBpm,
                    aiSuggested = true
                )
            )
        }
    }

    private fun getThisWeekSunday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        return cal.timeInMillis
    }
}
