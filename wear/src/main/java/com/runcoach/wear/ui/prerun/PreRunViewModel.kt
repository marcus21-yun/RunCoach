package com.runcoach.wear.ui.prerun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.wear.data.WearDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PreRunUiState(
    // 지난주 기록
    val lastDistanceKm: String = "-",
    val lastPace: String = "--:--",
    val lastAvgHr: String = "--",
    // 오늘 목표 (AI 제안)
    val targetKm: String = "-",
    val targetPace: String = "--:--",
    val speedupKm: String = "-",
    val speedupPct: Int = 0,
    // 기억하는 코치: 오늘 할 행동 + 이유 (수락한 목표가 코치 제안일 때만)
    val coachHeadline: String? = null,
    val coachReason: String? = null
)

@HiltViewModel
class PreRunViewModel @Inject constructor(
    private val dataStore: WearDataStore
) : ViewModel() {

    val uiState = dataStore.getCachedData().map { cache ->
        val fromCoach = cache.coachReasons.isNotEmpty() &&
            cache.currentGoal != null && cache.currentGoal == cache.nextGoal
        PreRunUiState(
            lastDistanceKm = "%.1f".format(cache.lastRecord?.distanceKm ?: 0f),
            lastPace       = cache.lastRecord?.avgPace ?: "--:--",
            lastAvgHr      = cache.lastRecord?.avgHeartRate?.toString() ?: "--",
            targetKm       = "%.1f".format(cache.currentGoal?.targetKm ?: 0f),
            targetPace     = cache.currentGoal?.targetPace ?: "--:--",
            speedupKm      = "%.1f".format(cache.currentGoal?.speedupKm ?: 0f),
            speedupPct     = cache.currentGoal?.speedupPct ?: 0,
            coachHeadline  = if (fromCoach) cache.aiMessage else null,
            coachReason    = if (fromCoach) cache.coachReasons.firstOrNull() else null
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreRunUiState())
}
