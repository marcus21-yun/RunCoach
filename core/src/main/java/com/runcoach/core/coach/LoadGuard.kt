package com.runcoach.core.coach

import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 나이대. 실력 추정에는 쓰지 않고, 회복 간격과 심박 기준을 정하는 데만 쓴다.
 * 나이를 입력하지 않으면 [UNKNOWN]으로 두고 사용자가 정한 기본 심박 기준(150/160)을 쓴다.
 */
enum class AgeBand(val label: String) {
    UNKNOWN("미입력"),
    UNDER_30("20대 이하"),
    AGE_30S("30대"),
    AGE_40S("40대"),
    AGE_50S("50대"),
    AGE_60_PLUS("60대 이상");

    companion object {
        fun of(age: Int?): AgeBand = when {
            age == null || age <= 0 -> UNKNOWN
            age < 30 -> UNDER_30
            age < 40 -> AGE_30S
            age < 50 -> AGE_40S
            age < 60 -> AGE_50S
            else -> AGE_60_PLUS
        }
    }
}

/** 최근 4주 실제 기록으로 나눈 러닝 수준. */
enum class RunnerLevel(val label: String) {
    /** 주 5km 미만 또는 주 1회 미만 */
    START("입문·복귀"),
    /** 주 5~15km */
    BASE("초급"),
    /** 주 15~30km */
    STEADY("중급"),
    /** 주 30km 이상 */
    STRONG("상급")
}

enum class LoadStatus {
    /** 기록이 쌓이는 중 (비교할 이전 주가 없음) */
    BUILDING,
    /** 이전 3주 평균 대비 10% 이내 증가 */
    SAFE,
    /** 10~30% 증가 */
    CAUTION,
    /** 30% 넘게 증가 — 입문자 부상 위험 증가 구간 (Nielsen 2014) */
    OVERLOAD
}

data class LoadAssessment(
    val level: RunnerLevel,
    val ageBand: AgeBand,
    /** 최근 7일 누적 거리 */
    val thisWeekKm: Float,
    /** 그 이전 3주(8~28일 전)의 주 평균 거리 */
    val baselineWeekKm: Float,
    /** 이번 주(최근 7일) 권장 상한 */
    val weeklyCapKm: Float,
    /** 상한까지 남은 거리 (0 이상) */
    val remainingKm: Float,
    val status: LoadStatus,
    /** 힘든 러닝 뒤 권장 휴식일 */
    val restDaysAfterHard: Int,
    /** 마지막 러닝 후 경과 일수 (기록 없으면 null) */
    val daysSinceLast: Int?,
    /** 습관 코치의 호흡·걷기 안내 심박 */
    val breathingBpm: Int,
    val walkBpm: Int,
    /** 한 줄 상태 설명 */
    val summary: String
)

/**
 * 무리하지 않는 러닝 패턴 가드.
 *
 * - 수준은 나이가 아니라 최근 4주 실제 주간 기록으로 나눈다.
 * - 주간 거리 증가는 이전 3주 평균의 10% 이내를 권장하고, 30%를 넘으면 과부하로 본다.
 *   (입문 러너 874명 연구에서 2주간 30% 초과 증가 시 거리 관련 부상이 늘었다: Nielsen 2014)
 * - 나이대는 회복 간격과 심박 기준에만 반영한다. 최대심박 추정은 208 - 0.7 × 나이 (Tanaka 2001).
 * - 모든 수치는 일반 권장이며 의학적 처방이 아니다. 사용자 설정이 항상 우선한다.
 */
object LoadGuard {

    private const val GROWTH = 0.10f
    private const val OVERLOAD_RATIO = 1.30f
    private const val CAUTION_RATIO = 1.10f

    /** 기록이 적어도 주 2회 × 3km는 허용 */
    private const val START_FLOOR_KM = 6f

    fun assess(
        runs: List<PatternRun>,
        age: Int?,
        now: Long = System.currentTimeMillis(),
        defaultConfig: HabitCoachConfig = HabitCoachConfig()
    ): LoadAssessment {
        val dayMs = TimeUnit.DAYS.toMillis(1)
        fun ago(r: PatternRun) = now - r.finishedAt
        val last7 = runs.filter { ago(it) in 0 until 7 * dayMs }
        val prev21 = runs.filter { ago(it) in 7 * dayMs until 28 * dayMs }
        val last28 = runs.filter { ago(it) in 0 until 28 * dayMs }

        val thisWeekKm = last7.sumOf { it.distanceKm.toDouble() }.toFloat()
        val baselineWeekKm = prev21.sumOf { it.distanceKm.toDouble() }.toFloat() / 3f
        val avgWeekKm = last28.sumOf { it.distanceKm.toDouble() }.toFloat() / 4f
        val runsPerWeek = last28.size / 4f

        val level = when {
            avgWeekKm < 5f || runsPerWeek < 1f -> RunnerLevel.START
            avgWeekKm < 15f -> RunnerLevel.BASE
            avgWeekKm < 30f -> RunnerLevel.STEADY
            else -> RunnerLevel.STRONG
        }

        val status = when {
            baselineWeekKm <= 0f -> LoadStatus.BUILDING
            thisWeekKm > baselineWeekKm * OVERLOAD_RATIO -> LoadStatus.OVERLOAD
            thisWeekKm > baselineWeekKm * CAUTION_RATIO -> LoadStatus.CAUTION
            else -> LoadStatus.SAFE
        }

        val weeklyCapKm = roundKm(max(START_FLOOR_KM, baselineWeekKm * (1f + GROWTH)))
        val remainingKm = roundKm((weeklyCapKm - thisWeekKm).coerceAtLeast(0f))

        val band = AgeBand.of(age)
        val restDays = when (band) {
            AgeBand.AGE_50S, AgeBand.AGE_60_PLUS -> 2
            else -> 1
        } + if (level == RunnerLevel.START) 1 else 0

        val (breathing, walk) = heartRateThresholds(age, defaultConfig)
        val daysSinceLast = runs.maxOfOrNull { it.finishedAt }?.let { ((now - it) / dayMs).toInt() }

        val summary = when (status) {
            LoadStatus.BUILDING -> "${level.label} · 이번 주 ${fmt(thisWeekKm)}km. 기록이 쌓이는 중이에요."
            LoadStatus.SAFE -> "${level.label} · 이번 주 ${fmt(thisWeekKm)}/${fmt(weeklyCapKm)}km. 무리 없는 흐름이에요."
            LoadStatus.CAUTION -> "${level.label} · 평소보다 ${pct(thisWeekKm, baselineWeekKm)}% 늘었어요. 이번 주는 여기까지가 좋아요."
            LoadStatus.OVERLOAD -> "${level.label} · 평소보다 ${pct(thisWeekKm, baselineWeekKm)}% 늘었어요. 쉬어가는 날이 필요해요."
        }

        return LoadAssessment(
            level = level,
            ageBand = band,
            thisWeekKm = roundKm(thisWeekKm),
            baselineWeekKm = roundKm(baselineWeekKm),
            weeklyCapKm = weeklyCapKm,
            remainingKm = remainingKm,
            status = status,
            restDaysAfterHard = restDays,
            daysSinceLast = daysSinceLast,
            breathingBpm = breathing,
            walkBpm = walk,
            summary = summary
        )
    }

    /**
     * 나이를 입력하면 추정 최대심박의 80%에서 호흡 안내, 88%에서 걷기 권유.
     * 나이를 모르면 사용자가 정한 기본값(150/160)을 그대로 쓴다.
     */
    fun heartRateThresholds(age: Int?, defaultConfig: HabitCoachConfig = HabitCoachConfig()): Pair<Int, Int> {
        if (age == null || age <= 0) return defaultConfig.breathingBpm to defaultConfig.walkBpm
        val maxHr = 208 - 0.7 * age
        return (maxHr * 0.80).roundToInt() to (maxHr * 0.88).roundToInt()
    }

    /** 평가 결과를 습관 코치 설정에 반영한다. */
    fun habitConfig(a: LoadAssessment, base: HabitCoachConfig = HabitCoachConfig()) =
        base.copy(breathingBpm = a.breathingBpm, walkBpm = a.walkBpm)

    private fun pct(now: Float, base: Float) = if (base <= 0f) 0 else ((now / base - 1f) * 100).roundToInt()
    private fun roundKm(v: Float) = (v * 10f).roundToInt() / 10f
    private fun fmt(v: Float): String {
        val r = roundKm(v)
        return if (r % 1f == 0f) r.toInt().toString() else "%.1f".format(r)
    }
}
