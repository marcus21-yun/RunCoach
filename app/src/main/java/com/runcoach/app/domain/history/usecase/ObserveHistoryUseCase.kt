package com.runcoach.app.domain.history.usecase

import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.domain.history.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveHistoryUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    operator fun invoke(): Flow<List<RunningRecord>> = repository.observeRecords()
}
