package com.runcoach.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.app.data.db.RunningRecordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class StatsMode { WEEKLY, MONTHLY }

data class StatsUiState(
    val totalKm: String = "0.0",
    val runCount: Int = 0,
    val avgPace: String = "--:--",
    val distanceData: List<Float> = emptyList(),
    val paceData: List<String> = emptyList(),
    val hrData: List<Int> = emptyList(),
    val labels: List<String> = emptyList()
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val recordDao: RunningRecordDao
) : ViewModel() {

    private val _mode = MutableStateFlow(StatsMode.WEEKLY)

    val uiState: StateFlow<StatsUiState> = combine(
        recordDao.getAllRecords(),
        _mode
    ) { records, mode ->
        when (mode) {
            StatsMode.WEEKLY  -> buildWeeklyState(records)
            StatsMode.MONTHLY -> buildMonthlyState(records)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun setMode(mode: StatsMode) { _mode.value = mode }

    // 최근 6주 데이터
    private fun buildWeeklyState(records: List<com.runcoach.app.data.db.RunningRecord>): StatsUiState {
        val cal = Calendar.getInstance()
        val weeks = (0..5).map { weeksAgo ->
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.WEEK_OF_YEAR, -weeksAgo)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val weekStart = cal.timeInMillis
            val weekEnd = weekStart + 7 * 24 * 60 * 60 * 1000L
            val label = SimpleDateFormat("M/d", Locale.KOREAN).format(Date(weekStart))
            Triple(weekStart, weekEnd, label)
        }.reversed()

        val weeklyData = weeks.map { (start, end, _) ->
            records.filter { it.date in start until end }
        }

        // 이번 주 (마지막)
        val thisWeek = weeklyData.lastOrNull() ?: emptyList()

        return StatsUiState(
            totalKm      = "%.1f".format(thisWeek.sumOf { it.distanceKm.toDouble() }),
            runCount     = thisWeek.size,
            avgPace      = avgPaceStr(thisWeek.map { paceToSeconds(it.avgPace) }),
            distanceData = weeklyData.map { w -> w.sumOf { it.distanceKm.toDouble() }.toFloat() },
            paceData     = weeklyData.map { w -> avgPaceStr(w.map { paceToSeconds(it.avgPace) }) },
            hrData       = weeklyData.map { w -> if (w.isEmpty()) 0 else w.map { it.avgHeartRate }.average().toInt() },
            labels       = weeks.map { it.third }
        )
    }

    // 최근 6개월 데이터
    private fun buildMonthlyState(records: List<com.runcoach.app.data.db.RunningRecord>): StatsUiState {
        val cal = Calendar.getInstance()
        val months = (0..5).map { monthsAgo ->
            cal.timeInMillis = System.currentTimeMillis()
            cal.add(Calendar.MONTH, -monthsAgo)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
            val monthStart = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            val monthEnd = cal.timeInMillis
            val label = SimpleDateFormat("M월", Locale.KOREAN).format(Date(monthStart))
            Triple(monthStart, monthEnd, label)
        }.reversed()

        val monthlyData = months.map { (start, end, _) ->
            records.filter { it.date in start until end }
        }
        val thisMonth = monthlyData.lastOrNull() ?: emptyList()

        return StatsUiState(
            totalKm      = "%.1f".format(thisMonth.sumOf { it.distanceKm.toDouble() }),
            runCount     = thisMonth.size,
            avgPace      = avgPaceStr(thisMonth.map { paceToSeconds(it.avgPace) }),
            distanceData = monthlyData.map { m -> m.sumOf { it.distanceKm.toDouble() }.toFloat() },
            paceData     = monthlyData.map { m -> avgPaceStr(m.map { paceToSeconds(it.avgPace) }) },
            hrData       = monthlyData.map { m -> if (m.isEmpty()) 0 else m.map { it.avgHeartRate }.average().toInt() },
            labels       = months.map { it.third }
        )
    }

    private fun paceToSeconds(pace: String): Int {
        val parts = pace.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun avgPaceStr(list: List<Int>): String {
        if (list.isEmpty()) return "--:--"
        val avg = list.average().toInt()
        return "%d:%02d".format(avg / 60, avg % 60)
    }
}
