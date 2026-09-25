package com.runcoach.app.data.history

import com.runcoach.app.data.db.BriefingRecordDao
import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.data.db.RunningRecordDao
import com.runcoach.app.data.health.SamsungHealthImportManager
import com.runcoach.app.data.supabase.SupabaseSyncService
import com.runcoach.app.domain.history.HistoryRepository
import com.runcoach.app.domain.history.ImportRunsResult
import com.runcoach.app.domain.history.ShareRunPayload
import com.runcoach.app.domain.history.SharedRunComparisonModel
import com.runcoach.app.domain.history.SyncOutcome
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultHistoryRepository @Inject constructor(
    private val recordDao: RunningRecordDao,
    private val briefingRecordDao: BriefingRecordDao,
    private val healthImportManager: SamsungHealthImportManager,
    private val supabaseSyncService: SupabaseSyncService
) : HistoryRepository {

    override fun observeRecords(): Flow<List<RunningRecord>> = recordDao.getAllRecords()

    override fun getSamsungHealthSetupMessage(): String? {
        return healthImportManager.getAvailabilityMessage()
    }

    override suspend fun importFromSamsungHealth(): ImportRunsResult {
        val result = healthImportManager.importRecentRuns()
        return ImportRunsResult(
            importedCount = result.importedCount,
            skippedCount = result.skippedCount
        )
    }

    override suspend fun syncToSupabase(): SyncOutcome {
        val records = recordDao.getAllRecordsSync()
        val briefingMap = records.associate { record ->
            record.id to briefingRecordDao.getLatestForRun(record.id)
        }
        val result = supabaseSyncService.syncRecords(records, briefingMap)
        return SyncOutcome(success = result.success, message = result.message)
    }

    override suspend fun shareRecord(recordId: Long): ShareRunPayload {
        val record = recordDao.getById(recordId)
            ?: error("선택한 러닝 기록을 찾지 못했습니다.")
        val briefing = briefingRecordDao.getLatestForRun(recordId)
        val result = supabaseSyncService.createShare(record, briefing)
        val share = result.share ?: error(result.message)
        return ShareRunPayload(
            code = share.code,
            qrPayload = share.qrPayload,
            briefing = share.briefing,
            distanceKm = share.distanceKm,
            avgPace = share.avgPace
        )
    }

    override suspend fun compareSharedCode(code: String): SharedRunComparisonModel {
        val trimmedCode = code.trim()
        require(trimmedCode.isNotBlank()) { "공유 코드를 입력해 주세요." }

        val remote = supabaseSyncService.fetchSharedRun(trimmedCode)
            ?: error("해당 공유 코드를 찾지 못했습니다.")
        val localRecord = recordDao.getLastRecord()

        return SharedRunComparisonModel(
            shareCode = remote.shareCode,
            recordedAt = remote.recordedAt,
            distanceKm = remote.distanceKm,
            avgPace = remote.avgPace,
            avgHeartRate = remote.avgHeartRate,
            durationSec = remote.durationSec,
            source = remote.source,
            briefing = remote.briefing,
            localRecord = localRecord,
            distanceDeltaKm = (localRecord?.distanceKm ?: 0f) - remote.distanceKm,
            paceDeltaSec = paceToSeconds(localRecord?.avgPace ?: "--:--") - paceToSeconds(remote.avgPace)
        )
    }

    private fun paceToSeconds(pace: String): Int {
        val parts = pace.split(":")
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return minutes * 60 + seconds
    }
}
