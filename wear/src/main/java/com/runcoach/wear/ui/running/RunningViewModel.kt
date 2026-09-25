package com.runcoach.wear.ui.running

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.runcoach.wear.alarm.AlarmTriggerManager
import com.runcoach.wear.alarm.AlarmType
import com.runcoach.wear.data.CachedGoal
import com.runcoach.wear.data.WearDataStore
import com.runcoach.wear.sensor.RunningRepository
import com.runcoach.wear.sensor.SensorData
import com.runcoach.wear.sensor.WearRunningService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RunningUiState(
    val distanceKm: Float = 0f,
    val currentPace: String = "--:--",
    val heartRate: Int = 0,
    val diffFromLastKm: Float = 0f,
    val elapsedSec: Int = 0,
    val fatigueLevel: String = "low",
    val fatigueLabel: String = "낮음",
    val gpsReady: Boolean = false
)

data class AlarmEvent(
    val type: AlarmType,
    val title: String,
    val message: String
)

@HiltViewModel
class RunningViewModel @Inject constructor(
    private val repository: RunningRepository,
    private val alarmManager: AlarmTriggerManager,
    private val dataStore: WearDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunningUiState())
    val uiState: StateFlow<RunningUiState> = _uiState.asStateFlow()

    private val _activeAlarm = MutableStateFlow<AlarmEvent?>(null)
    val activeAlarm: StateFlow<AlarmEvent?> = _activeAlarm.asStateFlow()

    // WearRunningService가 운동 종료 시 채워주는 결과 — PostBriefing에서 사용
    val runResult = repository.completedResult

    private var lastDistanceMilestone = 0
    private var speedupAlarmFired = false
    private var maxHeartRate = 0
    private var hrSum = 0
    private var hrCount = 0

    init {
        observeSensorData()
    }

    private fun observeSensorData() {
        viewModelScope.launch {
            combine(
                repository.sensorData,
                dataStore.getCachedData(),
                repository.gpsReady
            ) { data, cache, gpsReady -> Triple(data, cache, gpsReady) }
                .collect { (data, cache, gpsReady) ->
                val lastRecord = cache.lastRecord
                val goal = cache.currentGoal
                val fatigue = calcFatigueLevel(data.heartRate, data.elapsedSec)
                if (data.heartRate > 0) {
                    hrSum += data.heartRate
                    hrCount++
                    if (data.heartRate > maxHeartRate) maxHeartRate = data.heartRate
                }
                _uiState.update {
                    it.copy(
                        distanceKm     = data.distanceKm,
                        currentPace    = data.pacePerKm,
                        heartRate      = data.heartRate,
                        elapsedSec     = data.elapsedSec,
                        diffFromLastKm = data.distanceKm - (lastRecord?.distanceKm ?: 0f),
                        fatigueLevel   = fatigue,
                        fatigueLabel   = fatigueLabelOf(fatigue),
                        gpsReady       = gpsReady
                    )
                }
                checkAlarmTriggers(data, goal)
            }
        }
    }

    private fun checkAlarmTriggers(data: SensorData, goal: CachedGoal?) {
        if (goal == null) return

        // 1순위: 심박 경고
        if (data.heartRate > goal.hrAlertBpm) {
            triggerAlarm(
                type    = AlarmType.HEART_RATE,
                title   = "심박수 높음",
                message = "현재 ${data.heartRate}bpm\n페이스 줄이세요"
            )
            return
        }

        // 2순위: 구간 목표 (스피드업 구간, 1회만)
        if (!speedupAlarmFired && data.distanceKm >= goal.speedupKm) {
            speedupAlarmFired = true
            triggerAlarm(
                type    = AlarmType.SPEEDUP,
                title   = "%.1fkm 돌파!".format(goal.speedupKm),
                message = "스피드업 시간\n목표 +${goal.speedupPct}% 🚀"
            )
            return
        }

        // 3순위: 1km 단위 거리 달성
        val milestone = data.distanceKm.toInt()
        if (milestone > lastDistanceMilestone && milestone > 0) {
            lastDistanceMilestone = milestone
            val faster = data.paceSeconds < paceStringToSeconds(goal.targetPace)
            triggerAlarm(
                type    = AlarmType.DISTANCE,
                title   = "${milestone}km 달성!",
                message = "${data.pacePerKm}  ${if (faster) "목표보다 빠름 🟢" else "목표보다 느림 🔴"}"
            )
            return
        }

        // 4순위: 페이스 이탈 (목표보다 10% 느림)
        // 단, 실제로 이동 중(유효 페이스)일 때만 — 정지/ GPS 미수신 시 paceSeconds=MAX_VALUE 로
        // 경보가 매초 오발동하는 것을 방지
        val targetSec = paceStringToSeconds(goal.targetPace)
        val hasValidPace = data.paceSeconds in 1 until Int.MAX_VALUE && data.distanceKm > 0f
        if (targetSec > 0 && hasValidPace && data.paceSeconds > targetSec * 1.1) {
            triggerAlarm(
                type    = AlarmType.PACE_DROP,
                title   = "페이스 저하",
                message = "${data.pacePerKm} 🔴\n힘내세요!"
            )
        }
    }

    private fun triggerAlarm(type: AlarmType, title: String, message: String) {
        viewModelScope.launch {
            alarmManager.vibrate(type)
            _activeAlarm.value = AlarmEvent(type, title, message)
            kotlinx.coroutines.delay(type.displayMs)
            if (_activeAlarm.value?.type == type) {
                _activeAlarm.value = null
            }
        }
    }

    fun dismissAlarm() {
        _activeAlarm.value = null
    }

    fun startRun(context: Context) {
        val intent = Intent(context, WearRunningService::class.java).apply {
            action = WearRunningService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    // 종료 버튼 → WearRunningService에 STOP 명령 (서비스가 RunResult를 repository에 저장)
    fun finishRun(context: Context) {
        val intent = Intent(context, WearRunningService::class.java).apply {
            action = WearRunningService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun calcFatigueLevel(heartRate: Int, elapsedSec: Int): String {
        val hrScore = when {
            heartRate > 170 -> 3
            heartRate > 155 -> 2
            else            -> 1
        }
        val timeScore = when {
            elapsedSec > 2400 -> 2
            elapsedSec > 1200 -> 1
            else              -> 0
        }
        return when (hrScore + timeScore) {
            in 4..5 -> "high"
            in 2..3 -> "mid"
            else    -> "low"
        }
    }

    private fun fatigueLabelOf(level: String) = when (level) {
        "high" -> "높음"
        "mid"  -> "보통"
        else   -> "낮음"
    }

    // "6:30" 형식 → 초로 변환
    private fun paceStringToSeconds(pace: String): Int = runCatching {
        val parts = pace.split(":")
        parts[0].toInt() * 60 + parts[1].toInt()
    }.getOrDefault(0)
}
