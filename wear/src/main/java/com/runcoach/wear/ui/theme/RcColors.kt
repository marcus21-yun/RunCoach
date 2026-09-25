package com.runcoach.wear.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * RunCoach 워치 색상 토큰 (가독성/대비 검증 기반)
 * - 근거: docs/WATCH_UI_READABILITY_SPEC.md (Wear OS 가이드 + WCAG)
 */
object RcColors {
    val Background = Color(0xFF0F1115)   // 순흑 대신 — 눈피로·할레이션 완화
    val Surface = Color(0xFF1A1F2E)      // 카드 배경
    val SurfaceAlt = Color(0xFF16213E)   // 카드 그라데이션 끝

    val TextPrimary = Color(0xFFFFFFFF)  // 핵심 대형 숫자 전용
    val TextBody = Color(0xFFE9ECF1)     // 본문 흰색(순백 할레이션 완화)
    val TextSecondary = Color(0xFFB8C2D0) // 라벨·보조 (기존 #888888 대체, ≥4.5:1)

    val Accent = Color(0xFF4FC3F7)       // 브랜드·강조
    val Success = Color(0xFF00E676)      // 빠름·완주 (배지/아이콘)
    val Warning = Color(0xFFFFB74D)      // GPS 탐색·주의 (배지/아이콘)
    val Danger = Color(0xFFFF5252)       // 심박 경고·느림 (배지/아이콘)

    val Divider = Color(0xFF333A48)      // 구분선
}
