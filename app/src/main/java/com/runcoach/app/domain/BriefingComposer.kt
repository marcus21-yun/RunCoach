package com.runcoach.app.domain

import com.runcoach.app.data.db.RunningRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BriefingComposer @Inject constructor() {

    fun composePostRunBriefing(record: RunningRecord): String {
        val dateLabel = SimpleDateFormat("M월 d일", Locale.KOREAN).format(Date(record.date))
        val completion = if (record.completed) "목표를 완주했습니다." else "목표에 도전한 러닝이었습니다."

        return buildString {
            append(dateLabel)
            append(" 러닝 브리핑입니다. ")
            append("${formatDistance(record.distanceKm)}를 ")
            append("${record.avgPace}/km 페이스로 달렸고, ")
            append("평균 심박수는 ${record.avgHeartRate}bpm, 최고 심박수는 ${record.maxHeartRate}bpm이었습니다. ")
            append(completion)
            append(" 피로도는 ${formatFatigue(record.fatigueLevel)}로 기록됐고, ")
            append("다음 러닝에서는 ${record.targetPace}/km 페이스를 기준으로 호흡을 더 안정적으로 가져가 보세요.")
        }
    }

    private fun formatDistance(distanceKm: Float): String = String.format(Locale.US, "%.1fkm", distanceKm)

    private fun formatFatigue(fatigueLevel: String): String = when (fatigueLevel.lowercase(Locale.US)) {
        "low" -> "낮음"
        "mid" -> "보통"
        "high" -> "높음"
        else -> fatigueLevel
    }
}
