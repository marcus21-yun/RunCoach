package com.runcoach.core

/**
 * 50~60대 기준 실시간 심박 존.
 * app 모듈(AiGoalAdvisor)과 wear 모듈(WearRunningService) 공용.
 */
enum class HrZone { SAFE, CAUTION, DANGER }

/**
 * 나이 기반 HR 존 계산 (220 - 나이 = 최대 심박수 추정).
 * @param bpm   현재 심박수
 * @param age   사용자 나이 (기본값 55세)
 */
fun getHrZone(bpm: Int, age: Int = 55): HrZone {
    if (bpm <= 0) return HrZone.SAFE
    val maxHr = 220 - age
    return when {
        bpm < maxHr * 0.65 -> HrZone.SAFE
        bpm < maxHr * 0.78 -> HrZone.CAUTION
        else -> HrZone.DANGER
    }
}

/** DANGER 존 임계값 (bpm) */
fun getDangerThreshold(age: Int): Int = ((220 - age) * 0.78).toInt()
