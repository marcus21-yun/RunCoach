package com.runcoach.app.domain.history.usecase

import com.runcoach.app.domain.history.HistoryRepository
import com.runcoach.app.domain.history.ImportRunsResult
import javax.inject.Inject

class ImportSamsungHealthRunsUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    suspend operator fun invoke(): ImportRunsResult = repository.importFromSamsungHealth()
}
