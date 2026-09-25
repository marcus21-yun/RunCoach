package com.runcoach.wear.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.wear.data.WearDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class HomeUiState(
    val daysLeft: Int = 365,
    val progressPct: Float = 0f,
    val targetKm: String = "-",
    val targetPace: String = "--:--",
    val lastDistanceKm: String = "-"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val dataStore: WearDataStore
) : ViewModel() {

    val uiState = combine(
        dataStore.getCachedData(),
        dataStore.getStartDateFlow()
    ) { cache, startDate ->
        val maxKm = (cache.recentRecords.maxOfOrNull { it.distanceKm } ?: 0f)
            .takeIf { it.isFinite() && it >= 0f } ?: 0f
        val progressPct = ((maxKm / 21.0975f).takeIf { it.isFinite() } ?: 0f)
            .coerceIn(0f, 1f)
        HomeUiState(
            daysLeft = calcDaysLeft(startDate),
            progressPct = progressPct,
            targetKm = "%.1f".format(cache.currentGoal?.targetKm ?: 0f),
            targetPace = cache.currentGoal?.targetPace ?: "--:--",
            lastDistanceKm = "%.1f".format(cache.lastRecord?.distanceKm ?: 0f)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private fun calcDaysLeft(startDate: Long): Int {
        val targetDate = startDate + TimeUnit.DAYS.toMillis(365)
        val diff = targetDate - System.currentTimeMillis()
        return (diff / TimeUnit.DAYS.toMillis(1)).toInt().coerceAtLeast(0)
    }

    // 앱 최초 실행 시 시작일 저장
    fun initStartDateIfNeeded() {
        viewModelScope.launch {
            dataStore.initStartDateIfNeeded()
        }
    }

    // 폰에 최신 목표 요청
    fun requestGoalFromPhone() {
        viewModelScope.launch {
            dataStore.requestGoalFromPhone()
        }
    }
}
