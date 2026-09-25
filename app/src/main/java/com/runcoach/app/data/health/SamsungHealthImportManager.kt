package com.runcoach.app.data.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.permission.HealthPermission
import com.runcoach.app.data.db.BriefingRecord
import com.runcoach.app.data.db.BriefingRecordDao
import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.data.db.RunningRecordDao
import com.runcoach.app.domain.BriefingComposer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SamsungHealthImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runningRecordDao: RunningRecordDao,
    private val briefingRecordDao: BriefingRecordDao,
    private val briefingComposer: BriefingComposer
) {
    companion object {
        val requiredPermissions = setOf(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
    }

    fun getAvailabilityMessage(): String? {
        val packageManager = context.packageManager
        return try {
            packageManager.getPackageInfo("com.google.android.apps.healthdata", PackageManager.GET_ACTIVITIES)
            null
        } catch (_: PackageManager.NameNotFoundException) {
            "Health Connect 설치가 필요합니다. 삼성헬스에서 Health Connect 동기화도 켜주세요."
        }
    }

    suspend fun importRecentRuns(limit: Int = 20): ImportResult = withContext(Dispatchers.IO) {
        getAvailabilityMessage()?.let { error(it) }
        val client = HealthConnectClient.getOrCreate(context)
        val sessions = client.readRecords(
            ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.before(Instant.now()),
                ascendingOrder = false,
                pageSize = limit
            )
        ).records

        var imported = 0
        var skipped = 0

        sessions.forEach { session ->
            val externalId = session.metadata.id
            if (runningRecordDao.getByExternalId(externalId) != null) {
                skipped++
                return@forEach
            }

            val aggregate = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        DistanceRecord.DISTANCE_TOTAL,
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MAX
                    ),
                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime)
                )
            )

            val distanceKm = (aggregate[DistanceRecord.DISTANCE_TOTAL] ?: Length.meters(0.0)).inKilometers.toFloat()
            val avgHeartRate = aggregate[HeartRateRecord.BPM_AVG]?.toInt() ?: 0
            val maxHeartRate = aggregate[HeartRateRecord.BPM_MAX]?.toInt() ?: avgHeartRate
            val durationSec = (session.endTime.epochSecond - session.startTime.epochSecond).toInt().coerceAtLeast(1)
            if (distanceKm <= 0f) {
                skipped++
                return@forEach
            }
            val avgPace = formatPace(durationSec, distanceKm)

            val record = RunningRecord(
                date = session.startTime.toEpochMilli(),
                distanceKm = distanceKm,
                targetKm = distanceKm,
                avgPace = avgPace,
                targetPace = avgPace,
                avgHeartRate = avgHeartRate,
                maxHeartRate = maxHeartRate,
                durationSec = durationSec,
                fatigueLevel = inferFatigue(avgHeartRate),
                completed = true,
                source = "samsung_health",
                externalId = externalId,
                sourcePackage = session.metadata.dataOrigin.packageName
            )

            val rowId = runningRecordDao.insert(record)
            briefingRecordDao.insert(
                BriefingRecord(
                    runRecordId = rowId,
                    briefingKey = "run-$rowId-post",
                    briefingType = "post_run_summary",
                    content = briefingComposer.composePostRunBriefing(record.copy(id = rowId)),
                    createdAt = System.currentTimeMillis(),
                    provider = "template"
                )
            )
            imported++
        }

        ImportResult(importedCount = imported, skippedCount = skipped)
    }

    private fun formatPace(durationSec: Int, distanceKm: Float): String {
        val secondsPerKm = (durationSec / distanceKm).toInt()
        return "%d:%02d".format(secondsPerKm / 60, secondsPerKm % 60)
    }

    private fun inferFatigue(avgHeartRate: Int): String = when {
        avgHeartRate >= 170 -> "high"
        avgHeartRate >= 145 -> "mid"
        else -> "low"
    }
}

data class ImportResult(
    val importedCount: Int,
    val skippedCount: Int
)
