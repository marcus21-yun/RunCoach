package com.runcoach.core.coach

import kotlin.math.sqrt

/**
 * 러닝 중 1회 센서 샘플.
 * @param paceSec 현재 페이스(초/km). 정지·GPS 미수신 등 유효하지 않으면 null.
 * @param bpm 심박수. 측정 실패 시 0.
 */
data class HabitSample(
    val elapsedSec: Int,
    val distanceKm: Float,
    val paceSec: Int?,
    val bpm: Int
)

enum class CueType {
    /** 심박이 매우 높음 — 걷기 허용 (안전, 최우선) */
    SAFETY_SLOW,
    /** 심박이 호흡 기준에 가까워짐 — 호흡 리듬 안내 */
    BREATHING,
    /** 호흡 구간이 계속됨 — 보폭·내쉬기 재안내 */
    BREATHING_REPEAT,
    /** 시작 직후 — 천천히 시작하는 습관 */
    WARMUP,
    /** 지난 러닝에서 처졌던 지점에 다가감 */
    PATTERN_AHEAD,
    /** 속도가 평소보다 떨어짐 — 힘내기 */
    PACE_DROP,
    /** 초반에 너무 빠름 — 속도 줄이기 */
    TOO_FAST,
    /** 속도가 들쭉날쭉함 — 일정하게 */
    UNSTEADY,
    /** 오래 조용했을 때 자세 잔소리 */
    POSTURE,
    /** 목표 거리 500m 전 */
    FINAL_PUSH
}

data class HabitCue(
    val type: CueType,
    val bpm: Int = 0,
    val km: Float = 0f,
    val repeatCount: Int = 0,
    /** POSTURE 문구 순환용 */
    val variant: Int = 0
)

/** 지난 러닝들에서 반복해서 속도가 떨어진 지점. */
data class SlowdownPattern(
    /** 느려지기 시작한 km 지점 (예: 2.0 = 2km 지나서) */
    val km: Float,
    /** 최근 3회 중 같은 지점(±0.5km)에서 느려진 횟수 */
    val repeatCount: Int
)

/**
 * 기본값은 사용자가 정한 "심박 150 근처에서 호흡 안내" 기준이다.
 * 의학적 기준이 아니며 사용자·파일럿 결과에 따라 조정한다.
 */
data class HabitCoachConfig(
    /** 이 심박에 가까워지면 호흡 안내 */
    val breathingBpm: Int = 150,
    /** breathingBpm - margin 부터 "가깝다"고 본다 */
    val approachMarginBpm: Int = 5,
    /** 이 심박 이상이면 속도를 줄이고 걸어도 된다고 안내 */
    val walkBpm: Int = 160,
    /** 음성 안내 사이 최소 간격 (안전 안내 제외) */
    val minGapSec: Int = 20,
    /** 페이스 잔소리는 이 시간 이후부터 (워밍업 보호) */
    val paceCoachStartSec: Int = 180,
    /** 기준 대비 이만큼 느리면 PACE_DROP */
    val slowRatio: Double = 1.08,
    /** 기준 대비 이만큼 빠르면 TOO_FAST (초반만) */
    val fastRatio: Double = 0.90,
    val tooFastUntilSec: Int = 900,
    /** 60초 페이스 변동계수가 이 값을 넘으면 UNSTEADY */
    val unsteadyCv: Double = 0.12,
    /** 이 시간 동안 아무 말이 없으면 자세 잔소리 */
    val postureQuietSec: Int = 480
)

/**
 * 러닝 중 습관 코치.
 *
 * 목표 달성보다 "편한 호흡, 고른 페이스, 반복해서 무너지는 지점 넘기기" 같은
 * 러닝 습관을 만드는 데 초점을 둔다. 언제 말할지는 이 규칙이 정하고,
 * 어떤 말투로 말할지는 [HabitPhrasebook]이 정한다. 네트워크 없이 동작한다.
 *
 * 원칙:
 * - 안전 안내가 항상 먼저다.
 * - 숨이 찬 동안(호흡 구간)에는 절대 속도를 더 내라고 하지 않는다.
 * - 같은 종류의 잔소리는 쿨다운을 두고, 안내 사이 최소 간격을 지킨다.
 */
class RunHabitCoach(
    private val config: HabitCoachConfig = HabitCoachConfig(),
    private val targetPaceSec: Int? = null,
    private val targetKm: Float? = null,
    private val pattern: SlowdownPattern? = null
) {
    private val lastByType = mutableMapOf<CueType, Int>()
    /** 러닝 시작 시점을 마지막 안내로 본다 (시작 직후 자세 잔소리 방지) */
    private var lastCueAt = 0
    private var inBreathZone = false
    private var lastBreathCueAt = Int.MIN_VALUE / 2
    private var breathIntroDone = false
    private var warmupDone = false
    private var patternDone = false
    private var finalDone = false
    private var postureVariant = 0
    private val paceWindow = ArrayDeque<Pair<Int, Int>>()

    fun onSample(s: HabitSample): HabitCue? {
        val t = s.elapsedSec
        s.paceSec?.takeIf { it in 120..1200 }?.let { paceWindow.addLast(t to it) }
        while (paceWindow.isNotEmpty() && t - paceWindow.first().first > 60) paceWindow.removeFirst()

        // 1) 안전: 최소 간격을 무시하고 바로 안내
        if (s.bpm >= config.walkBpm && t - (lastByType[CueType.SAFETY_SLOW] ?: Int.MIN_VALUE / 2) >= 45) {
            return emit(HabitCue(CueType.SAFETY_SLOW, bpm = s.bpm), t)
        }

        // 2) 호흡 구간 (히스테리시스: 145 진입, 140 미만 이탈)
        val enterBpm = config.breathingBpm - config.approachMarginBpm
        if (!inBreathZone && s.bpm >= enterBpm) {
            inBreathZone = true
            // 들락날락할 때 같은 안내를 반복하지 않도록 60초 안의 재진입은 첫 안내를 생략
            breathIntroDone = t - lastBreathCueAt < 60
        } else if (inBreathZone && s.bpm in 1 until enterBpm - 5) {
            inBreathZone = false
        }
        if (inBreathZone && t - lastCueAt >= config.minGapSec) {
            if (!breathIntroDone) {
                breathIntroDone = true
                lastBreathCueAt = t
                return emit(HabitCue(CueType.BREATHING, bpm = s.bpm), t)
            }
            if (t - lastBreathCueAt >= 60) {
                lastBreathCueAt = t
                return emit(HabitCue(CueType.BREATHING_REPEAT, bpm = s.bpm), t)
            }
        }

        // 3) 시작 습관
        if (!warmupDone && t >= 5) {
            warmupDone = true
            return emit(HabitCue(CueType.WARMUP), t)
        }

        // 4) 지난번 처졌던 지점 미리 알림
        if (pattern != null && !patternDone && s.distanceKm >= pattern.km - 0.2f) {
            patternDone = true
            if (!inBreathZone && s.distanceKm < pattern.km + 0.5f && t - lastCueAt >= config.minGapSec) {
                return emit(HabitCue(CueType.PATTERN_AHEAD, km = pattern.km, repeatCount = pattern.repeatCount), t)
            }
        }

        // 5) 마무리 응원
        if (targetKm != null && targetKm >= 1f && !finalDone && s.distanceKm >= targetKm - 0.5f) {
            finalDone = true
            if (!inBreathZone && t - lastCueAt >= config.minGapSec) {
                return emit(HabitCue(CueType.FINAL_PUSH, km = targetKm), t)
            }
        }

        // 6) 페이스 습관 — 숨이 찬 동안에는 재촉하지 않는다
        if (t >= config.paceCoachStartSec && !inBreathZone) {
            val recent = paceWindow.filter { t - it.first <= 30 }.map { it.second }
            val covered = recent.size >= 5 && paceWindow.isNotEmpty() && t - paceWindow.first().first >= 20
            val ref = targetPaceSec ?: runAveragePace(s)
            if (covered && ref != null) {
                val avg = recent.average()
                if (avg > ref * config.slowRatio && canSpeak(CueType.PACE_DROP, t, 90)) {
                    return emit(HabitCue(CueType.PACE_DROP), t)
                }
                if (avg < ref * config.fastRatio && t < config.tooFastUntilSec && canSpeak(CueType.TOO_FAST, t, 120)) {
                    return emit(HabitCue(CueType.TOO_FAST), t)
                }
            }
            if (paceWindow.size >= 10 && coefficientOfVariation(paceWindow.map { it.second }) > config.unsteadyCv &&
                canSpeak(CueType.UNSTEADY, t, 180)
            ) {
                return emit(HabitCue(CueType.UNSTEADY), t)
            }
        }

        // 7) 오래 조용하면 자세 잔소리
        if (t - lastCueAt >= config.postureQuietSec && canSpeak(CueType.POSTURE, t, 600)) {
            return emit(HabitCue(CueType.POSTURE, variant = postureVariant++), t)
        }
        return null
    }

    private fun canSpeak(type: CueType, t: Int, cooldownSec: Int): Boolean =
        t - lastCueAt >= config.minGapSec &&
            t - (lastByType[type] ?: Int.MIN_VALUE / 2) >= cooldownSec

    private fun emit(cue: HabitCue, t: Int): HabitCue {
        lastByType[cue.type] = t
        lastCueAt = t
        return cue
    }

    /** 목표 페이스가 없으면 이번 러닝의 평균 페이스를 기준으로 삼는다. */
    private fun runAveragePace(s: HabitSample): Int? =
        if (s.distanceKm >= 0.5f) (s.elapsedSec / s.distanceKm).toInt() else null

    private fun coefficientOfVariation(values: List<Int>): Double {
        val mean = values.average()
        if (mean <= 0) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance) / mean
    }
}
