package com.runcoach.core.coach

import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 러닝 후 사용자가 직접 고른 체감 난이도.
 * 센서 수치(심박·피로도)와 별개로 "내가 느낀 것"을 다음 제안의 근거로 쓴다.
 */
enum class Effort(val id: String, val label: String) {
    EASY("easy", "쉬웠어요"),
    OK("ok", "적당해요"),
    HARD("hard", "힘들었어요");

    companion object {
        fun fromId(id: String?): Effort? = entries.firstOrNull { it.id == id }
    }
}

/** 코치가 판단에 사용하는 러닝 1회 요약. */
data class RunSnapshot(
    val finishedAt: Long,
    val distanceKm: Float,
    /** 평균 페이스(초/km). 측정 실패 시 null. */
    val paceSec: Int?,
    val completed: Boolean,
    /** "low" / "mid" / "high" (WearRunningService.calcFatigue) */
    val fatigueLevel: String,
    val effort: Effort? = null
)

data class GoalSnapshot(
    val targetKm: Float,
    val targetPaceSec: Int?
)

enum class CoachMode {
    /** 기록 없음 — 첫 러닝 */
    START,
    /** 거리를 조금 늘림 */
    BUILD,
    /** 같은 목표 유지 */
    HOLD,
    /** 목표를 낮춰 회복 */
    EASE,
    /** 오래 쉰 뒤 복귀 */
    RETURN
}

data class CoachPlan(
    val mode: CoachMode,
    val targetKm: Float,
    val targetPaceSec: Int?,
    val speedupKm: Float,
    val speedupPct: Int,
    /** 오늘 할 행동 한 문장 */
    val headline: String,
    /** 제안 근거. 모두 저장된 기록·사용자 피드백에서 나온 사실만 담는다. */
    val reasons: List<String>
)

/**
 * "기억하는 코치" 규칙 엔진.
 *
 * - 훈련 수치는 검토 가능한 규칙으로만 결정한다(생성형 AI는 설명만 담당).
 * - 한 번에 하나만 바꾼다: 거리를 늘리는 주에는 페이스를 올리지 않는다.
 * - 거리 증가는 직전 목표의 10%, 최대 1km로 제한한다.
 * - 네트워크·폰 없이 워치 단독으로 동작한다.
 *
 * 의학적 판단이 아니며, 수치 기준은 파일럿 검증 후 조정한다.
 */
object MemoryCoach {

    const val MIN_KM = 2.0f
    const val START_KM = 3.0f
    const val MAX_KM = 21.1f

    const val RETURN_GAP_DAYS = 14
    const val EASE_GAP_DAYS = 8

    private const val BUILD_RATIO_EASY = 0.10f
    private const val BUILD_CAP_EASY = 1.0f
    private const val BUILD_RATIO_OK = 0.05f
    private const val BUILD_CAP_OK = 0.5f

    /**
     * @param runs 최신순 기록 목록
     * @param currentGoal 사용자가 수락한 현재 목표 (없으면 최근 기록 기준)
     */
    fun plan(
        runs: List<RunSnapshot>,
        currentGoal: GoalSnapshot?,
        now: Long = System.currentTimeMillis()
    ): CoachPlan {
        val last = runs.firstOrNull() ?: return startPlan(currentGoal)

        val baseKm = (currentGoal?.targetKm ?: last.distanceKm)
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(MIN_KM, MAX_KM)
            ?: START_KM
        val basePace = currentGoal?.targetPaceSec ?: last.paceSec
        val gapDays = TimeUnit.MILLISECONDS.toDays((now - last.finishedAt).coerceAtLeast(0)).toInt()

        if (gapDays >= RETURN_GAP_DAYS) {
            val km = roundKm(min(baseKm, max(last.distanceKm, MIN_KM)) * 0.6f)
            return CoachPlan(
                mode = CoachMode.RETURN,
                targetKm = km,
                targetPaceSec = basePace?.plus(30),
                speedupKm = 0f,
                speedupPct = 0,
                headline = "다시 시작하는 날이에요. ${fmtKm(km)}km를 편하게 달려봐요.",
                reasons = listOf(
                    "마지막 러닝 후 ${gapDays}일이 지났어요.",
                    "복귀 첫 러닝은 평소 목표의 60% 거리로 시작해요."
                )
            )
        }

        if (gapDays >= EASE_GAP_DAYS) {
            val km = roundKm(baseKm * 0.8f)
            return CoachPlan(
                mode = CoachMode.EASE,
                targetKm = km,
                targetPaceSec = basePace?.plus(15),
                speedupKm = 0f,
                speedupPct = 0,
                headline = "오랜만이에요. 오늘은 ${fmtKm(km)}km로 몸을 깨워봐요.",
                reasons = listOf(
                    "마지막 러닝 후 ${gapDays}일이 지났어요.",
                    "쉬었던 만큼 거리를 20% 줄여 다시 적응해요."
                )
            )
        }

        val effort = last.effort ?: inferEffort(last.fatigueLevel)
        val effortReason = if (last.effort != null) {
            "지난번에 '${last.effort.label}'라고 하셨어요."
        } else {
            "지난번 체감 기록이 없어 피로도(${fatigueLabel(last.fatigueLevel)})로 판단했어요."
        }

        val recentHard = runs.take(3).count { (it.effort ?: inferEffort(it.fatigueLevel)) == Effort.HARD }
        if (recentHard >= 2 && runs.size >= 2) {
            val km = roundKm(if (last.completed) baseKm else max(MIN_KM, baseKm - 0.5f))
            return CoachPlan(
                mode = if (last.completed) CoachMode.HOLD else CoachMode.EASE,
                targetKm = km,
                targetPaceSec = basePace?.plus(15),
                speedupKm = 0f,
                speedupPct = 0,
                headline = "오늘은 페이스를 늦추고 ${fmtKm(km)}km를 끝까지 편하게 가요.",
                reasons = listOf(
                    "최근 ${min(runs.size, 3)}번 중 ${recentHard}번이 힘들었어요.",
                    "거리를 늘리기 전에 편하게 완주하는 것을 먼저 만들어요."
                )
            )
        }

        return when {
            effort == Effort.HARD && !last.completed -> {
                val km = roundKm(max(MIN_KM, baseKm - 0.5f))
                CoachPlan(
                    mode = CoachMode.EASE,
                    targetKm = km,
                    targetPaceSec = basePace?.plus(15),
                    speedupKm = 0f,
                    speedupPct = 0,
                    headline = "처음 1km를 천천히 시작해서 ${fmtKm(km)}km 완주에 집중해요.",
                    reasons = listOf(effortReason, "목표를 0.5km 낮춰 완주 경험을 먼저 쌓아요.")
                )
            }

            effort == Effort.HARD -> CoachPlan(
                mode = CoachMode.HOLD,
                targetKm = roundKm(baseKm),
                targetPaceSec = basePace?.plus(10),
                speedupKm = 0f,
                speedupPct = 0,
                headline = "같은 ${fmtKm(baseKm)}km를 조금 더 여유 있는 페이스로 달려요.",
                reasons = listOf(effortReason, "거리는 그대로, 페이스만 10초 늦춰요.")
            )

            !last.completed -> CoachPlan(
                mode = CoachMode.HOLD,
                targetKm = roundKm(baseKm),
                targetPaceSec = basePace,
                speedupKm = 0f,
                speedupPct = 0,
                headline = "지난 목표 ${fmtKm(baseKm)}km를 다시 한 번 완주해봐요.",
                reasons = listOf(
                    effortReason,
                    "지난번 ${fmtKm(last.distanceKm)}km로 목표에 조금 못 미쳤어요."
                )
            )

            effort == Effort.EASY -> buildPlan(baseKm, basePace, BUILD_RATIO_EASY, BUILD_CAP_EASY, 5, effortReason)

            else -> buildPlan(baseKm, basePace, BUILD_RATIO_OK, BUILD_CAP_OK, 3, effortReason)
        }
    }

    private fun buildPlan(
        baseKm: Float,
        basePace: Int?,
        ratio: Float,
        cap: Float,
        speedupPct: Int,
        effortReason: String
    ): CoachPlan {
        val add = min(baseKm * ratio, cap)
        val km = roundKm(min(baseKm + add, MAX_KM))
        return CoachPlan(
            mode = CoachMode.BUILD,
            targetKm = km,
            // 거리를 늘리는 주에는 페이스를 올리지 않는다
            targetPaceSec = basePace,
            speedupKm = roundKm(km * 0.6f),
            speedupPct = speedupPct,
            headline = "오늘은 ${fmtKm(km)}km, 페이스는 지난번 그대로 가요.",
            reasons = listOf(
                effortReason,
                "거리만 ${fmtKm(km - roundKm(baseKm))}km 늘리고 페이스는 유지해요."
            )
        )
    }

    private fun startPlan(currentGoal: GoalSnapshot?): CoachPlan {
        val km = currentGoal?.targetKm?.takeIf { it.isFinite() && it > 0f }?.let(::roundKm) ?: START_KM
        return CoachPlan(
            mode = CoachMode.START,
            targetKm = km,
            targetPaceSec = currentGoal?.targetPaceSec,
            speedupKm = 0f,
            speedupPct = 0,
            headline = "첫 러닝이에요. ${fmtKm(km)}km를 대화할 수 있는 속도로 달려봐요.",
            reasons = listOf("아직 저장된 기록이 없어요. 오늘 기록부터 기억할게요.")
        )
    }

    fun inferEffort(fatigueLevel: String): Effort = when (fatigueLevel) {
        "high" -> Effort.HARD
        "low" -> Effort.EASY
        else -> Effort.OK
    }

    private fun fatigueLabel(level: String) = when (level) {
        "high" -> "높음"
        "low" -> "낮음"
        else -> "보통"
    }

    /** 0.1km 단위 반올림, 최소 거리 보장 */
    private fun roundKm(km: Float): Float =
        ((km * 10f).roundToInt() / 10f).coerceIn(MIN_KM, MAX_KM)

    private fun fmtKm(km: Float): String {
        val r = (km * 10f).roundToInt() / 10f
        return if (r % 1f == 0f) r.toInt().toString() else "%.1f".format(r)
    }
}

/** "6:30" → 390. 파싱 불가("--:--" 등)면 null. */
fun paceToSecondsOrNull(pace: String?): Int? {
    val parts = pace?.split(":") ?: return null
    if (parts.size != 2) return null
    val m = parts[0].toIntOrNull() ?: return null
    val s = parts[1].toIntOrNull() ?: return null
    return (m * 60 + s).takeIf { it > 0 }
}

/** 390 → "6:30". null이면 "--:--". */
fun secondsToPace(seconds: Int?): String =
    if (seconds == null || seconds <= 0) "--:--"
    else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
