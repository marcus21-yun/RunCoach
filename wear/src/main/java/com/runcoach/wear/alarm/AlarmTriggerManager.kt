package com.runcoach.wear.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AlarmType(val displayMs: Long) {
    HEART_RATE(3000L),   // 심박 경고 — 3초
    SPEEDUP(3000L),      // 구간 목표 — 3초
    DISTANCE(2000L),     // 거리 달성 — 2초
    PACE_DROP(2000L)     // 페이스 이탈 — 2초
}

@Singleton
class AlarmTriggerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun vibrate(type: AlarmType) {
        val pattern = when (type) {
            AlarmType.HEART_RATE -> longArrayOf(0, 200, 100, 200, 100, 200) // 3회 강하게
            AlarmType.SPEEDUP    -> longArrayOf(0, 150, 100, 150)            // 2회 중간
            AlarmType.DISTANCE   -> longArrayOf(0, 100)                      // 1회 짧게
            AlarmType.PACE_DROP  -> longArrayOf(0, 150, 100, 150)            // 2회
        }
        val amplitude = when (type) {
            AlarmType.HEART_RATE -> 255  // 최강
            AlarmType.SPEEDUP    -> 180
            AlarmType.DISTANCE   -> 120
            AlarmType.PACE_DROP  -> 160
        }
        // amplitude를 패턴 길이에 맞게 배열로 변환
        val amplitudes = IntArray(pattern.size) { i -> if (i % 2 == 0) 0 else amplitude }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    fun cancel() = vibrator.cancel()
}
