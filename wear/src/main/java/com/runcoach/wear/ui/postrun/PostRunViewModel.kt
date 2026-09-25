package com.runcoach.wear.ui.postrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.wear.data.WearDataStore
import com.runcoach.wear.sensor.RunningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PostRunUiState(
    val distanceKm: String = "0.0",
    val avgPace: String = "--:--",
    val duration: String = "--:--",
    val avgHr: Int = 0,
    val fatigueLevel: String = "low",
    val fatigueLabel: String = "낮음",
    val completed: Boolean = false,
    val diffDistance: String = "-",
    val diffDistancePos: Boolean = true,
    val diffPace: String = "-",
    val diffPacePos: Boolean = true
)

@HiltViewModel
class PostRunViewModel @Inject constructor(
    private val repository: RunningRepository,
    private val dataStore: WearDataStore
) : ViewModel() {

    val uiState = combine(
        repository.completedResult,
        dataStore.getCachedData()
    ) { result, cache ->
        val lastKm = cache.lastRecord?.distanceKm ?: 0f
        val distDiff = (result?.distanceKm ?: 0f) - lastKm
        val distDiffStr = if (distDiff >= 0) "+%.1fkm ↑".format(distDiff)
                          else "%.1fkm ↓".format(distDiff)

        PostRunUiState(
            distanceKm    = "%.1f".format(result?.distanceKm ?: 0f),
            avgPace       = result?.avgPace ?: "--:--",
            duration      = formatSeconds(result?.durationSec ?: 0),
            avgHr         = result?.avgHeartRate ?: 0,
            fatigueLevel  = result?.fatigueLevel ?: "low",
            fatigueLabel  = fatigueLabelOf(result?.fatigueLevel ?: "low"),
            completed     = result?.completed ?: false,
            diffDistance  = distDiffStr,
            diffDistancePos = distDiff >= 0,
            diffPace      = calcPaceDiff(result?.avgPace, cache.lastRecord?.avgPace),
            diffPacePos   = isPaceFaster(result?.avgPace, cache.lastRecord?.avgPace)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PostRunUiState())

    private fun formatSeconds(sec: Int): String {
        val m = sec / 60; val s = sec % 60
        return "%02d:%02d".format(m, s)
    }

    private fun fatigueLabelOf(level: String) = when (level) {
        "high" -> "높음 🔴"; "mid" -> "보통 🟡"; else -> "낮음 🟢"
    }

    private fun calcPaceDiff(current: String?, last: String?): String {
        if (current == null || last == null) return "-"
        val curSec = paceToSeconds(current)
        val lastSec = paceToSeconds(last)
        val diff = lastSec - curSec // 양수 = 빨라짐
        return if (diff >= 0) "+${diff}초 빨라짐 ↑" else "${-diff}초 느려짐 ↓"
    }

    private fun isPaceFaster(current: String?, last: String?): Boolean {
        if (current == null || last == null) return true
        return paceToSeconds(current) <= paceToSeconds(last)
    }

    private fun paceToSeconds(pace: String): Int {
        val parts = pace.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
