package com.runcoach.app.domain

import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.data.db.WeeklyGoal
import com.runcoach.core.HrZone
import com.runcoach.core.getDangerThreshold
import com.runcoach.core.getHrZone
import javax.inject.Inject

class AiGoalAdvisor @Inject constructor() {

    private val halfMarathonKm = 21.0975f

    // 50~60대 기본 나이 (사용자 프로필 미연동 시 사용)
    private val defaultAge = 55

    fun suggestNextGoal(
        lastRecord: RunningRecord,
        currentGoal: WeeklyGoal?,
        halfMarathonDate: Long = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000,
        userAge: Int = defaultAge
    ): AiSuggestion {
        val hrDangerBpm = getDangerThreshold(userAge)
        val goal = currentGoal ?: WeeklyGoal(
            weekStart = System.currentTimeMillis(),
            targetKm = lastRecord.distanceKm,
            targetPace = lastRecord.avgPace,
            speedupKm = lastRecord.distanceKm * 0.6f,
            speedupPct = 3,
            hrAlertBpm = hrDangerBpm,
            aiSuggested = true
        )

        val weeksLeft = weeksUntil(halfMarathonDate)
        val weeklyNeeded = (halfMarathonKm - lastRecord.distanceKm) / weeksLeft
        val nextKm = calcNextDistance(lastRecord, goal, weeklyNeeded)
        val nextPace = calcNextPace(lastRecord, goal)

        return AiSuggestion(
            targetKm = nextKm,
            targetPace = nextPace,
            speedupKm = nextKm * 0.6f,
            speedupPct = calcSpeedupPct(lastRecord.fatigueLevel),
            hrAlertBpm = goal.hrAlertBpm,
            message = buildMessage(lastRecord)
        )
    }

    private fun calcNextDistance(record: RunningRecord, goal: WeeklyGoal, weeklyNeeded: Float): Float {
        val hrHigh = getHrZone(record.avgHeartRate) == HrZone.DANGER
        val proposed = when {
            record.fatigueLevel == "high" || hrHigh -> goal.targetKm
            !record.completed -> goal.targetKm - 0.5f
            record.fatigueLevel == "low" -> goal.targetKm + 1.0f
            else -> goal.targetKm + 0.5f
        }

        return proposed.coerceAtLeast(3f).coerceAtMost(goal.targetKm + weeklyNeeded * 1.5f)
    }

    private fun calcNextPace(record: RunningRecord, goal: WeeklyGoal): String {
        val currentPaceSeconds = paceToSeconds(record.avgPace)
        val delta = when {
            record.fatigueLevel == "high" -> 15
            !record.completed -> 0
            record.fatigueLevel == "low" -> -10
            else -> -5
        }
        return secondsToPace((currentPaceSeconds + delta).coerceAtLeast(0))
    }

    private fun calcSpeedupPct(fatigue: String) = when (fatigue) {
        "low" -> 5
        "mid" -> 3
        else -> 0
    }

    private fun buildMessage(record: RunningRecord): String = when {
        record.fatigueLevel == "high" || getHrZone(record.avgHeartRate) == HrZone.DANGER ->
            "이번 주는 회복 우선으로 가세요. 심박수가 높았으니 같은 거리에서 호흡을 안정시키는 쪽이 더 좋습니다."
        getHrZone(record.avgHeartRate) == HrZone.CAUTION ->
            "심박수가 조금 높았어요. 다음 주에는 페이스를 약간 낮춰 안전하게 달려보세요."
        !record.completed ->
            "완주를 먼저 안정적으로 만드는 편이 좋습니다. 다음 주는 거리를 조금 낮춰도 괜찮습니다."
        record.fatigueLevel == "low" ->
            "오늘 심장이 튼튼했어요! 다음 주에는 거리를 조금 올려도 충분히 감당할 수 있습니다."
        else ->
            "좋은 흐름입니다. 다음 주에는 0.5km 정도만 천천히 늘려보세요."
    }

    private fun weeksUntil(timestamp: Long): Float {
        val diffMs = timestamp - System.currentTimeMillis()
        return (diffMs / (1000f * 60 * 60 * 24 * 7)).coerceAtLeast(1f)
    }

    private fun paceToSeconds(pace: String): Int {
        val parts = pace.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return minutes * 60 + seconds
    }

    private fun secondsToPace(seconds: Int): String =
        "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

data class AiSuggestion(
    val targetKm: Float,
    val targetPace: String,
    val speedupKm: Float,
    val speedupPct: Int,
    val hrAlertBpm: Int,
    val message: String
)
