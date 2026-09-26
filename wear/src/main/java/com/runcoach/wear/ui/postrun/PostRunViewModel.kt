package com.runcoach.wear.ui.postrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.core.coach.Effort
import com.runcoach.core.coach.LoadGuard
import com.runcoach.core.coach.LoadStatus
import com.runcoach.wear.data.toPatternRun
import com.runcoach.core.coach.Reflection
import com.runcoach.core.coach.ReflectionCoach
import com.runcoach.core.coach.RunPatternAnalyzer
import com.runcoach.wear.data.CoachPlanner
import com.runcoach.wear.data.WearDataStore
import com.runcoach.wear.sensor.RunningRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val diffPacePos: Boolean = true,
    // 기억하는 코치: 체감 피드백
    val recordId: String? = null,
    val selectedEffort: Effort? = null,
    val coachHeadline: String? = null,
    // 멘탈 코치: 선수 패턴과의 비교 + 대화 주제
    val reflection: Reflection? = null,
    // 무리하지 않는 패턴: 수준 + 이번 주 누적 상태
    val loadSummary: String? = null,
    val loadWarning: Boolean = false
)

@HiltViewModel
class PostRunViewModel @Inject constructor(
    private val repository: RunningRepository,
    private val dataStore: WearDataStore,
    private val coachPlanner: CoachPlanner
) : ViewModel() {

    val uiState = combine(
        repository.completedResult,
        dataStore.getCachedData()
    ) { result, cache ->
        // 방금 끝난 러닝은 이미 저장되어 recentRecords[0]에 있으므로, 비교 대상은 그 이전 기록이다.
        val current = if (result != null) cache.recentRecords.firstOrNull() else null
        val previous = if (result != null) cache.recentRecords.getOrNull(1) else cache.lastRecord
        val effort = Effort.fromId(current?.effort)
        val patternRuns = cache.recentRecords.map { it.toPatternRun() }
        val load = LoadGuard.assess(patternRuns, cache.userAge)
        val lastKm = previous?.distanceKm ?: 0f
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
            diffPace      = calcPaceDiff(result?.avgPace, previous?.avgPace),
            diffPacePos   = isPaceFaster(result?.avgPace, previous?.avgPace),
            recordId      = current?.id,
            selectedEffort = effort,
            coachHeadline = if (effort != null && cache.coachReasons.isNotEmpty()) cache.aiMessage else null,
            reflection    = ReflectionCoach.reflect(RunPatternAnalyzer.profile(patternRuns)),
            loadSummary   = load.summary,
            loadWarning   = load.status == LoadStatus.CAUTION || load.status == LoadStatus.OVERLOAD
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PostRunUiState())

    init {
        // 워치 단독 모드: 피드백 전이라도 피로도 기준으로 다음 목표를 먼저 계산해 둔다.
        // 폰이 보낸 미수락 목표가 있으면 덮어쓰지 않는다.
        viewModelScope.launch {
            val result = repository.completedResult.first() ?: return@launch
            val cache = dataStore.getCachedData().first()
            if (result.durationSec > 0 && (cache.nextGoal == null || cache.nextGoal == cache.currentGoal)) {
                coachPlanner.replan()
            }
        }
    }

    /** 체감 난이도 저장 → 다음 목표를 워치에서 즉시 재계산 */
    fun submitFeedback(effort: Effort) {
        val recordId = uiState.value.recordId ?: return
        viewModelScope.launch {
            coachPlanner.submitFeedback(recordId, effort)
        }
    }

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
