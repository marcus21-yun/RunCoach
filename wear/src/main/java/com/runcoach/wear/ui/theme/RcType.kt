package com.runcoach.wear.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * RunCoach 워치 타이포그래피 토큰
 * - 12sp 미만 금지, 변동 숫자는 tabular(tnum)로 흔들림 방지
 * - 근거: docs/WATCH_UI_READABILITY_SPEC.md
 */
object RcType {
    /** 히어로 지표 1개 (러닝 중 = 현재 페이스/거리) */
    val Display = TextStyle(
        fontSize = 38.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    )

    /** 변동 숫자 (심박·거리·시간) — 자릿수 흔들림 방지 */
    val MetricValue = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    )

    /** 화면 제목·D-day */
    val Title = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)

    /** 카드 본문·메타데이터 */
    val Body = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)

    /** 라벨("페이스","심박","거리") */
    val Label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)

    /** 단위·필수 보조(하한 12sp) */
    val Caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
}
