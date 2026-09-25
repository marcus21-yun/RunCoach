package com.runcoach.app.domain.history.usecase

import com.runcoach.app.domain.history.HistoryRepository
import com.runcoach.app.domain.history.ShareRunPayload
import javax.inject.Inject

class ShareRunRecordUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    suspend operator fun invoke(recordId: Long): ShareRunPayload = repository.shareRecord(recordId)
}
