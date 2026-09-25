package com.runcoach.app.ui.prediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.app.data.db.RunningRecordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class RoadmapMilestone(
    val week: String,
    val km: Float,
    val isPastOrCurrent: Boolean,
    val isTarget: Boolean
)

data class PredictionUiState(
    val estimatedWeeks: String = "-",
    val estimatedFinishTime: String = "--:--:--",
    val currentPace: String = "--:--",
    val currentMaxKm: Float = 0f,
    val motivationMessage: String = "💪 함께 달려요!",
    val roadmap: List<RoadmapMilestone> = emptyList()
)

@HiltViewModel
class PredictionViewModel @Inject constructor(
    private val recordDao: RunningRecordDao
) : ViewModel() {

    val uiState: StateFlow<PredictionUiState> = recordDao.getAllRecords()
        .map { records -> buildState(records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PredictionUiState())

    private fun buildState(records: List<com.runcoach.app.data.db.RunningRecord>): PredictionUiState {
        if (records.isEmpty()) return PredictionUiState(
            estimatedWeeks = "52",
            motivationMessage = "첫 러닝을 시작해보세요! 🏃"
        )

        val maxKm = records.maxOf { it.distanceKm }
        val recentPaceSec = records.take(3).map { paceToSeconds(it.avgPace) }.average().toInt()
        val weeklyGain = 0.5f // 평균 주당 증가량

        val weeksLeft = ((21.0975f - maxKm) / weeklyGain).toInt().coerceAtLeast(1)
        val finishTimeSec = (21.0975 * recentPaceSec).toInt()
        val paceStr = "%d:%02d".format(recentPaceSec / 60, recentPaceSec % 60)

        val motivation = when {
            maxKm >= 20f -> "거의 다 왔어요! 마지막 스퍼트! 🔥"
            maxKm >= 15f -> "절반을 넘었어요! 이 페이스면 완주 가능! 💪"
            maxKm >= 10f -> "10km 돌파! 하프까지 반 왔어요! 🎯"
            maxKm >= 5f  -> "기초가 잡히고 있어요! 꾸준히 가요 🌱"
            else         -> "첫 걸음이 가장 중요해요! 잘 하고 있어요 😊"
        }

        // 로드맵 (현재 ~ 목표까지 주요 이정표)
        val milestones = listOf(
            RoadmapMilestone("현재",  maxKm,   true,  false),
            RoadmapMilestone("4주 후", maxKm + weeklyGain * 4,  maxKm + weeklyGain * 4 <= maxKm, false),
            RoadmapMilestone("8주 후", maxKm + weeklyGain * 8,  false, false),
            RoadmapMilestone("12주 후", maxKm + weeklyGain * 12, false, false),
            RoadmapMilestone("20주 후", maxKm + weeklyGain * 20, false, false),
            RoadmapMilestone("${weeksLeft}주 후", 21.0975f, false, true)
        ).map { it.copy(km = it.km.coerceAtMost(21.0975f)) }

        return PredictionUiState(
            estimatedWeeks      = "${weeksLeft}주",
            estimatedFinishTime = formatSeconds(finishTimeSec),
            currentPace         = "$paceStr/km",
            currentMaxKm        = maxKm,
            motivationMessage   = motivation,
            roadmap             = milestones
        )
    }

    private fun paceToSeconds(pace: String): Int {
        val parts = pace.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun formatSeconds(sec: Int): String {
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return "%d:%02d:%02d".format(h, m, s)
    }
}
