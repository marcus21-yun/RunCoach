package com.runcoach.wear.ui.goal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.wear.data.WearDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiGoalUiState(
    val aiMessage: String = "분석 중...",
    val nextKm: String = "-",
    val nextPace: String = "--:--",
    val nextSpeedupKm: String = "-",
    val nextSpeedupPct: Int = 0,
    val nextHrAlert: Int = 170,
    /** 기억하는 코치의 제안 근거 */
    val reasons: List<String> = emptyList()
)

@HiltViewModel
class AiGoalViewModel @Inject constructor(
    private val dataStore: WearDataStore
) : ViewModel() {

    val uiState = dataStore.getCachedData().map { cache ->
        val goal = cache.nextGoal
        AiGoalUiState(
            aiMessage     = cache.aiMessage ?: "기록을 분석하고 있어요...",
            nextKm        = "%.1f".format(goal?.targetKm ?: 0f),
            nextPace      = goal?.targetPace ?: "--:--",
            nextSpeedupKm = "%.1f".format(goal?.speedupKm ?: 0f),
            nextSpeedupPct = goal?.speedupPct ?: 0,
            nextHrAlert   = goal?.hrAlertBpm ?: 170,
            reasons       = cache.coachReasons
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiGoalUiState())

    // 목표 수락 → DataStore에 현재 목표로 저장
    fun acceptGoal() {
        viewModelScope.launch {
            dataStore.acceptNextGoal()
        }
    }
}
