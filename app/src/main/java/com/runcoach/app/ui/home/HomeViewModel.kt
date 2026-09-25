package com.runcoach.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.data.db.RunningRecordDao
import com.runcoach.app.data.db.WeeklyGoal
import com.runcoach.app.data.db.WeeklyGoalDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    recordDao: RunningRecordDao,
    goalDao: WeeklyGoalDao
) : ViewModel() {

    val currentGoal = goalDao.getCurrentGoal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastRecord = recordDao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 하프마라톤까지 남은 일수 (목표일: 앱 시작 후 1년)
    val daysLeft: Int get() {
        val targetMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(365)
        return ((targetMs - System.currentTimeMillis()) / TimeUnit.DAYS.toMillis(1)).toInt()
    }

    // 하프마라톤 진행률 (현재 최대 거리 / 21.0975)
    fun progressPct(records: List<RunningRecord>): Float {
        val maxKm = records.maxOfOrNull { it.distanceKm } ?: 0f
        return (maxKm / 21.0975f).coerceIn(0f, 1f)
    }
}
