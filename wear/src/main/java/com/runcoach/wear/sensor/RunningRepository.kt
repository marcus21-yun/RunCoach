package com.runcoach.wear.sensor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WearRunningService ↔ RunningViewModel 사이의 데이터 브릿지
 * - Service가 센서 데이터를 업데이트
 * - ViewModel이 StateFlow를 구독
 */
@Singleton
class RunningRepository @Inject constructor() {

    private val _sensorData = MutableStateFlow(SensorData())
    val sensorData: StateFlow<SensorData> = _sensorData.asStateFlow()

    private val _completedResult = MutableStateFlow<RunResult?>(null)
    val completedResult: StateFlow<RunResult?> = _completedResult.asStateFlow()

    // GPS 위성 신호 확보 여부 (false = 신호 찾는 중)
    private val _gpsReady = MutableStateFlow(false)
    val gpsReady: StateFlow<Boolean> = _gpsReady.asStateFlow()

    fun updateSensorData(data: SensorData) {
        _sensorData.value = data
    }

    fun updateGpsReady(ready: Boolean) {
        _gpsReady.value = ready
    }

    fun updateResult(result: RunResult) {
        _completedResult.value = result
    }

    fun reset() {
        _sensorData.value = SensorData()
        _completedResult.value = null
        _gpsReady.value = false
    }
}
