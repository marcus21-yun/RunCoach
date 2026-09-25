package com.runcoach.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.data.db.RunningRecordDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class RunDetailUiState(
    val dateLabel: String = "",
    val distanceKm: String = "0.0",
    val completed: Boolean = false,
    val duration: String = "--:--",
    val avgPace: String = "--:--",
    val targetPace: String = "--:--",
    val calorie: Int = 0,
    // 심박수
    val avgHr: Int = 0,
    val maxHr: Int = 0,
    val zone1Pct: Float = 0f,
    val zone2Pct: Float = 0f,
    val zone3Pct: Float = 0f,
    val zone4Pct: Float = 0f,
    // 구간 페이스
    val splitPaces: List<String> = emptyList(),
    val speedupKm: Float = 3f,
    // 지난주 대비
    val diffDistanceLabel: String = "-",
    val diffDistancePositive: Boolean = true,
    val diffPaceLabel: String = "-",
    val diffPacePositive: Boolean = true,
    val diffHrLabel: String = "-",
    val diffHrPositive: Boolean = false
)

@HiltViewModel
class RunDetailViewModel @Inject constructor(
    private val recordDao: RunningRecordDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(RunDetailUiState())
    val uiState: StateFlow<RunDetailUiState> = _uiState.asStateFlow()

    fun load(recordId: Long) {
        viewModelScope.launch {
            val record = recordDao.getById(recordId) ?: return@launch
            val allRecords = recordDao.getAllRecordsSync()
            val index = allRecords.indexOfFirst { it.id == recordId }
            val prevRecord = if (index < allRecords.size - 1) allRecords[index + 1] else null

            _uiState.value = buildState(record, prevRecord)
        }
    }

    private fun buildState(record: RunningRecord, prev: RunningRecord?): RunDetailUiState {
        val avgHr = record.avgHeartRate
        val maxHr = record.maxHeartRate

        // 심박수 존 추정 (최대 심박 기준 220 - 나이, 여기선 190 기본값)
        val maxPossibleHr = 190
        val zone1Threshold = (maxPossibleHr * 0.60).toInt()
        val zone2Threshold = (maxPossibleHr * 0.70).toInt()
        val zone3Threshold = (maxPossibleHr * 0.80).toInt()

        // 평균 심박 기반 존 추정
        val (z1, z2, z3, z4) = estimateZones(avgHr, zone1Threshold, zone2Threshold, zone3Threshold)

        // 구간별 페이스 (균등 분할 추정, 실제 구간 데이터 없을 경우)
        val totalKm = record.distanceKm.toInt().coerceAtLeast(1)
        val avgPaceSec = paceToSeconds(record.avgPace)
        val splitPaces = estimateSplitPaces(totalKm, avgPaceSec)

        // 지난주 대비
        val distDiff = prev?.let { record.distanceKm - it.distanceKm }
        val paceDiff = prev?.let { paceToSeconds(it.avgPace) - paceToSeconds(record.avgPace) }
        val hrDiff   = prev?.let { it.avgHeartRate - record.avgHeartRate }

        return RunDetailUiState(
            dateLabel     = SimpleDateFormat("M월 d일 (E) 러닝", Locale.KOREAN).format(Date(record.date)),
            distanceKm    = "%.2f".format(record.distanceKm),
            completed     = record.completed,
            duration      = formatSeconds(record.durationSec),
            avgPace       = record.avgPace,
            targetPace    = "--:--", // WeeklyGoal 조인 시 채움
            calorie       = 0,      // Samsung Health SDK 연동 시 채움
            avgHr         = avgHr,
            maxHr         = maxHr,
            zone1Pct      = z1,
            zone2Pct      = z2,
            zone3Pct      = z3,
            zone4Pct      = z4,
            splitPaces    = splitPaces,
            speedupKm     = 3f,
            diffDistanceLabel    = distDiff?.let { if (it >= 0) "+%.1fkm ↑".format(it) else "%.1fkm ↓".format(it) } ?: "-",
            diffDistancePositive = (distDiff ?: 0f) >= 0f,
            diffPaceLabel        = paceDiff?.let { if (it >= 0) "+${it}초 빨라짐 ↑" else "${-it}초 느려짐 ↓" } ?: "-",
            diffPacePositive     = (paceDiff ?: 0) >= 0,
            diffHrLabel          = hrDiff?.let { if (it >= 0) "-${it}bpm 낮아짐 ✅" else "+${-it}bpm 높아짐" } ?: "-",
            diffHrPositive       = (hrDiff ?: 0) >= 0
        )
    }

    private fun estimateZones(avgHr: Int, z1: Int, z2: Int, z3: Int): List<Float> {
        return when {
            avgHr <= z1  -> listOf(0.7f, 0.2f, 0.1f, 0.0f)
            avgHr <= z2  -> listOf(0.1f, 0.6f, 0.2f, 0.1f)
            avgHr <= z3  -> listOf(0.05f, 0.2f, 0.55f, 0.2f)
            else         -> listOf(0.0f, 0.1f, 0.35f, 0.55f)
        }
    }

    private fun estimateSplitPaces(totalKm: Int, avgPaceSec: Int): List<String> {
        return (1..totalKm).map { km ->
            // 후반부 페이스업 시뮬레이션
            val factor = when {
                km <= totalKm / 2 -> 1.05  // 전반 5% 느리게
                else              -> 0.95  // 후반 5% 빠르게
            }
            val sec = (avgPaceSec * factor).toInt()
            "%d:%02d".format(sec / 60, sec % 60)
        }
    }

    private fun paceToSeconds(pace: String): Int {
        val parts = pace.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun formatSeconds(sec: Int): String {
        val m = sec / 60; val s = sec % 60
        return "%02d:%02d".format(m, s)
    }
}
