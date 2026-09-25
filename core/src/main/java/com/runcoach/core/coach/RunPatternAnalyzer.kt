package com.runcoach.core.coach

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** 패턴 분석용 러닝 1회 요약. */
data class PatternRun(
    val finishedAt: Long,
    val distanceKm: Float,
    val durationSec: Int,
    val avgHeartRate: Int,
    /** 1km 구간별 소요 시간(초). 완주한 km만 담긴다. */
    val splitsSec: List<Int> = emptyList()
)

enum class PacingStyle {
    /** 후반이 1% 이상 빠름 */
    NEGATIVE,
    /** 전·후반 차이 ±3% 이내 */
    EVEN,
    /** 후반이 3% 이상 느림 */
    POSITIVE
}

/** 최근 기록에서 읽어낸 나의 러닝 습관. 값이 null이면 판단할 데이터가 없다는 뜻이다. */
data class RunPatternProfile(
    val runsLast28Days: Int,
    /** 최근 4주 중 한 번 이상 달린 주 수 */
    val activeWeeks: Int,
    /** 최근 4주 주당 평균 러닝 시간(분) */
    val weeklyMinutes: Int,
    /** 가장 자주 달린 요일과 그 비율 */
    val favoriteDay: DayOfWeek?,
    val favoriteDayShare: Float,
    /** 심박이 기록된 러닝 중 호흡 기준 미만(편한 강도)이었던 비율 */
    val easyShare: Float?,
    val runsWithHr: Int,
    /** 구간 기록이 있는 최근 러닝의 페이스 배분 */
    val latestPacing: PacingStyle?,
    /** 후반 페이스 변화율(+ 느려짐, - 빨라짐) */
    val latestSecondHalfChange: Float?,
    /** 구간 기록이 있는 최근 3회 중 후반에 느려진 횟수 */
    val positiveSplitCount: Int,
    val pacedRuns: Int,
    val slowdown: SlowdownPattern?
)

object RunPatternAnalyzer {

    private const val SLOW_RATIO = 1.08

    /**
     * 초반 2km 평균보다 8% 이상 느려지기 시작한 km 지점.
     * 예: splits[2]가 느리면 2.0 (2km 지나서 느려짐). 구간이 3개 미만이면 판단하지 않는다.
     */
    fun slowdownKm(splitsSec: List<Int>): Float? {
        if (splitsSec.size < 3) return null
        val base = (splitsSec[0] + splitsSec[1]) / 2.0
        for (i in 2 until splitsSec.size) {
            if (splitsSec[i] > base * SLOW_RATIO) return i.toFloat()
        }
        return null
    }

    /** 최근 3회 중 가장 최근의 느려진 지점과, 같은 지점(±0.5km) 반복 횟수 */
    fun slowdownPattern(recentSlowdownKms: List<Float?>): SlowdownPattern? {
        val recent = recentSlowdownKms.take(3)
        val latest = recent.firstOrNull { it != null } ?: return null
        val repeat = recent.count { it != null && abs(it - latest) <= 0.5f }
        return SlowdownPattern(latest, repeat)
    }

    /** 전반 대비 후반 평균 구간 시간의 변화율. 구간이 2개 미만이면 null. */
    fun secondHalfChange(splitsSec: List<Int>): Float? {
        if (splitsSec.size < 2) return null
        val half = splitsSec.size / 2
        val first = splitsSec.take(half).average()
        val second = splitsSec.drop(splitsSec.size - half).average()
        if (first <= 0) return null
        return ((second - first) / first).toFloat()
    }

    fun pacingStyle(change: Float): PacingStyle = when {
        change >= 0.03f -> PacingStyle.POSITIVE
        change <= -0.01f -> PacingStyle.NEGATIVE
        else -> PacingStyle.EVEN
    }

    /**
     * @param runs 최신순
     * @param breathingBpm 편한 강도의 기준 심박 (습관 코치의 호흡 기준과 같은 값)
     */
    fun profile(
        runs: List<PatternRun>,
        breathingBpm: Int = HabitCoachConfig().breathingBpm,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): RunPatternProfile {
        val dayMs = TimeUnit.DAYS.toMillis(1)
        val last28 = runs.filter { now - it.finishedAt in 0 until 28 * dayMs }
        val activeWeeks = last28.map { ((now - it.finishedAt) / (7 * dayMs)).toInt() }.distinct().size
        val weeklyMinutes = last28.sumOf { it.durationSec } / 60 / 4

        val days = last28.map { Instant.ofEpochMilli(it.finishedAt).atZone(zone).dayOfWeek }
        val favorite = days.groupingBy { it }.eachCount().maxByOrNull { it.value }

        val withHr = runs.take(10).filter { it.avgHeartRate > 0 }
        val easyShare = if (withHr.isEmpty()) null
        else withHr.count { it.avgHeartRate < breathingBpm }.toFloat() / withHr.size

        val paced = runs.filter { it.splitsSec.size >= 2 }.take(3)
        val latestChange = paced.firstOrNull()?.let { secondHalfChange(it.splitsSec) }
        val positiveCount = paced.count { r ->
            secondHalfChange(r.splitsSec)?.let { pacingStyle(it) == PacingStyle.POSITIVE } == true
        }

        return RunPatternProfile(
            runsLast28Days = last28.size,
            activeWeeks = activeWeeks,
            weeklyMinutes = weeklyMinutes,
            favoriteDay = favorite?.key,
            favoriteDayShare = if (last28.isEmpty()) 0f else (favorite?.value ?: 0).toFloat() / last28.size,
            easyShare = easyShare,
            runsWithHr = withHr.size,
            latestPacing = latestChange?.let(::pacingStyle),
            latestSecondHalfChange = latestChange,
            positiveSplitCount = positiveCount,
            pacedRuns = paced.size,
            slowdown = slowdownPattern(runs.map { slowdownKm(it.splitsSec) })
        )
    }
}
