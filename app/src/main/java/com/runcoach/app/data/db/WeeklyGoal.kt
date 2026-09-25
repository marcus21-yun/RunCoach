package com.runcoach.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_goals")
data class WeeklyGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val weekStart: Long,             // 해당 주 시작일 (timestamp)
    val targetKm: Float,             // 목표 거리 (km)
    val targetPace: String,          // 목표 페이스 (mm:ss)
    val speedupKm: Float,            // 스피드업 시작 구간 (km)
    val speedupPct: Int,             // 스피드업 비율 (%)
    val hrAlertBpm: Int,             // 심박수 경고 기준 (bpm)
    val aiSuggested: Boolean         // AI 제안 여부
)
