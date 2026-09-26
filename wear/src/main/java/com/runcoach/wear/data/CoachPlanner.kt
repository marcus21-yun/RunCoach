package com.runcoach.wear.data

import com.runcoach.core.coach.CoachPlan
import com.runcoach.core.coach.Effort
import com.runcoach.core.coach.GoalSnapshot
import com.runcoach.core.coach.LoadGuard
import com.runcoach.core.coach.MemoryCoach
import com.runcoach.core.coach.RunSnapshot
import com.runcoach.core.coach.paceToSecondsOrNull
import com.runcoach.core.coach.secondsToPace
import com.runcoach.core.getDangerThreshold
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기억하는 코치: 러닝 후 체감 피드백을 저장하고,
 * 저장된 기록만으로 다음 목표와 근거를 워치에서 바로 계산한다.
 */
@Singleton
class CoachPlanner @Inject constructor(
    private val dataStore: WearDataStore
) {

    suspend fun submitFeedback(recordId: String, effort: Effort): CoachPlan {
        dataStore.saveRunFeedback(recordId, effort.id)
        return replan()
    }

    suspend fun replan(): CoachPlan {
        val cache = dataStore.getCachedData().first()
        val plan = MemoryCoach.plan(
            runs = cache.recentRecords.map { it.toSnapshot() },
            currentGoal = cache.currentGoal?.let {
                GoalSnapshot(it.targetKm, paceToSecondsOrNull(it.targetPace))
            },
            // 이번 주 누적이 권장 상한을 넘지 않도록
            load = LoadGuard.assess(cache.recentRecords.map { it.toPatternRun() }, cache.userAge)
        )
        dataStore.saveCoachGoal(
            goal = CachedGoal(
                targetKm = plan.targetKm,
                targetPace = secondsToPace(plan.targetPaceSec),
                speedupKm = plan.speedupKm,
                speedupPct = plan.speedupPct,
                hrAlertBpm = cache.currentGoal?.hrAlertBpm ?: getDangerThreshold(DEFAULT_AGE)
            ),
            headline = plan.headline,
            reasons = plan.reasons
        )
        return plan
    }

    private fun CachedRecord.toSnapshot() = RunSnapshot(
        finishedAt = date,
        distanceKm = distanceKm,
        paceSec = paceToSecondsOrNull(avgPace),
        completed = completed,
        fatigueLevel = fatigueLevel,
        effort = Effort.fromId(effort)
    )

    private companion object {
        const val DEFAULT_AGE = 55
    }
}
