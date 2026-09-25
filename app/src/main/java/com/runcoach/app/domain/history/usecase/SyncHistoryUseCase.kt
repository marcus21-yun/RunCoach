package com.runcoach.app.domain.history.usecase

import com.runcoach.app.domain.history.HistoryRepository
import com.runcoach.app.domain.history.SyncOutcome
import javax.inject.Inject

class SyncHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    suspend operator fun invoke(): SyncOutcome = repository.syncToSupabase()
}
