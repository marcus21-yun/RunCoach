package com.runcoach.app.domain.history

import com.runcoach.app.data.db.RunningRecord
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun observeRecords(): Flow<List<RunningRecord>>
    fun getSamsungHealthSetupMessage(): String?
    suspend fun importFromSamsungHealth(): ImportRunsResult
    suspend fun syncToSupabase(): SyncOutcome
    suspend fun shareRecord(recordId: Long): ShareRunPayload
    suspend fun compareSharedCode(code: String): SharedRunComparisonModel
}

data class ImportRunsResult(
    val importedCount: Int,
    val skippedCount: Int
)

data class SyncOutcome(
    val success: Boolean,
    val message: String
)

data class ShareRunPayload(
    val code: String,
    val qrPayload: String,
    val briefing: String,
    val distanceKm: Float,
    val avgPace: String
)

data class SharedRunComparisonModel(
    val shareCode: String,
    val recordedAt: Long,
    val distanceKm: Float,
    val avgPace: String,
    val avgHeartRate: Int,
    val durationSec: Int,
    val source: String,
    val briefing: String,
    val localRecord: RunningRecord?,
    val distanceDeltaKm: Float,
    val paceDeltaSec: Int
)
