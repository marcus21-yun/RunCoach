package com.runcoach.wear.ui.briefing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.wear.BuildConfig
import com.runcoach.wear.data.BriefingRepository
import com.runcoach.wear.data.WearDataStore
import com.runcoach.wear.data.api.OpenAiApiService
import com.runcoach.wear.data.model.CoachStyle
import com.runcoach.wear.tts.BriefingTtsManager
import com.runcoach.wear.sensor.RunResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class BriefingUiState {
    object Loading : BriefingUiState()
    data class Ready(val text: String, val coachStyle: CoachStyle) : BriefingUiState()
    object Speaking : BriefingUiState()
    object Done : BriefingUiState()
    data class Error(val message: String) : BriefingUiState()
}

@HiltViewModel
class BriefingViewModel @Inject constructor(
    application: Application,
    private val wearDataStore: WearDataStore
) : AndroidViewModel(application) {

    private val repository = BriefingRepository(
        context = application,
        apiService = OpenAiApiService(apiKey = BuildConfig.OPENAI_API_KEY)
    )

    private val ttsManager = BriefingTtsManager(application).apply {
        onStart = { _uiState.value = BriefingUiState.Speaking }
        onDone  = { _uiState.value = BriefingUiState.Done }
        onError = { _uiState.value = BriefingUiState.Error("음성 재생 오류") }
    }

    private val _uiState = MutableStateFlow<BriefingUiState>(BriefingUiState.Loading)
    val uiState: StateFlow<BriefingUiState> = _uiState

    // ── 사전 브리핑: WearDataStore에서 실제 데이터 자동 로드 ──────

    fun loadAndSpeakPreBriefing() {
        _uiState.value = BriefingUiState.Loading
        viewModelScope.launch {
            val cache = wearDataStore.getCachedData().first()
            val last  = cache.lastRecord
            val goal  = cache.currentGoal
            val startDate = wearDataStore.getStartDate()
            val weeksLeft = ((startDate + TimeUnit.DAYS.toMillis(365) -
                System.currentTimeMillis()) /
                TimeUnit.DAYS.toMillis(7)).toInt().coerceAtLeast(1)

            val text = repository.getPreBriefing(
                lastKm    = last?.distanceKm ?: 0f,
                lastPace  = last?.avgPace ?: "--:--",
                completed = last?.completed ?: false,
                fatigue   = "보통",
                targetKm  = goal?.targetKm ?: 5f,
                targetPace = goal?.targetPace ?: "--:--",
                weeksLeft  = weeksLeft
            )
            val style = repository.loadCoachStyle()
            _uiState.value = BriefingUiState.Ready(text, style)
            ttsManager.speak(text, style)
        }
    }

    // ── 사후 브리핑: RunningViewModel이 만든 RunResult 사용 ────────

    fun loadAndSpeakPostBriefing(result: RunResult) {
        _uiState.value = BriefingUiState.Loading
        viewModelScope.launch {
            val goal       = wearDataStore.getCurrentGoalSync()
            val lastRecord = wearDataStore.getLastRecord()
            val targetKm   = goal?.targetKm ?: 5f
            val targetPace = goal?.targetPace ?: "--:--"
            val diffKm     = result.distanceKm - (lastRecord?.distanceKm ?: result.distanceKm)
            val nextKm     = (targetKm + 0.5f).coerceAtMost(targetKm * 1.2f)

            val text = repository.getPostBriefing(
                actualKm   = result.distanceKm,
                targetKm   = targetKm,
                completed  = result.completed,
                avgPace    = result.avgPace,
                targetPace = targetPace,
                avgHr      = result.avgHeartRate,
                maxHr      = result.maxHeartRate,
                fatigue    = result.fatigueLevel,
                diffKm     = diffKm,
                nextKm     = nextKm
            )
            val recordId = wearDataStore.getLastRecord()?.id
            if (recordId != null) {
                wearDataStore.saveBriefing(
                    recordId = recordId,
                    briefingType = "post_run",
                    content = text
                )
            }
            val style = repository.loadCoachStyle()
            _uiState.value = BriefingUiState.Ready(text, style)
            ttsManager.speak(text, style)
        }
    }

    // ── 건너뛰기 ───────────────────────────────────────────────────

    fun skip() {
        ttsManager.stop()
        _uiState.value = BriefingUiState.Done
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.release()
    }
}
