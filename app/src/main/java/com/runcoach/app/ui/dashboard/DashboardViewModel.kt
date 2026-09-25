package com.runcoach.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.data.db.RunningRecordDao
import com.runcoach.app.data.db.WeeklyGoalDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class RecordSummary(
    val id: Long,
    val date: Long,
    val distanceKm: Float,
    val avgPace: String,
    val avgHeartRate: Int,
    val completed: Boolean
)

data class DashboardUiState(
    val daysLeft: Int = 365,
    val progressPct: Float = 0f,
    val currentMaxKm: String = "0.0",
    val estimatedWeeks: Int = 52,
    val monthlyKm: String = "0.0",
    val monthlyCount: Int = 0,
    val monthlyAvgPace: String = "--:--",
    val monthlyAvgHr: Int = 0,
    val recentRecords: List<RecordSummary> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val recordDao: RunningRecordDao,
    private val goalDao: WeeklyGoalDao
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = recordDao.getAllRecords()
        .map { records -> buildState(records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private fun buildState(records: List<RunningRecord>): DashboardUiState {
        val maxKm = records.maxOfOrNull { it.distanceKm } ?: 0f
        val progressPct = (maxKm / 21.0975f).coerceIn(0f, 1f)
        val estimatedWeeks = if (maxKm > 0f)
            ((21.0975f - maxKm) / 0.5f).toInt().coerceAtLeast(1)
        else 52

        // 이번 달 필터
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val monthStart = cal.timeInMillis
        val monthly = records.filter { it.date >= monthStart }

        val monthlyKm = monthly.sumOf { it.distanceKm.toDouble() }.toFloat()
        val monthlyAvgPace = if (monthly.isEmpty()) "--:--"
            else avgPaceStr(monthly.map { paceToSeconds(it.avgPace) })
        val monthlyAvgHr = if (monthly.isEmpty()) 0
            else monthly.map { it.avgHeartRate }.average().toInt()

        val recentRecords = records.take(10).map {
            RecordSummary(it.id, it.date, it.distanceKm, it.avgPace, it.avgHeartRate, it.completed)
        }

        return DashboardUiState(
            daysLeft       = calcDaysLeft(),
            progressPct    = progressPct,
            currentMaxKm   = "%.1f".format(maxKm),
            estimatedWeeks = estimatedWeeks,
            monthlyKm      = "%.1f".format(monthlyKm),
            monthlyCount   = monthly.size,
            monthlyAvgPace = monthlyAvgPace,
            monthlyAvgHr   = monthlyAvgHr,
            recentRecords  = recentRecords
        )
    }

    private fun calcDaysLeft(): Int {
        // 앱 최초 설치일 기준 365일 (추후 DataStore로 관리)
        return 365
    }

    private fun paceToSeconds(pace: String): Int {
        val parts = pace.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun avgPaceStr(secondsList: List<Int>): String {
        val avg = secondsList.average().toInt()
        return "%d:%02d".format(avg / 60, avg % 60)
    }
}
