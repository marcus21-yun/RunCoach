package com.runcoach.app.domain.history.usecase

import com.runcoach.app.domain.history.HistoryRepository
import com.runcoach.app.domain.history.SharedRunComparisonModel
import javax.inject.Inject

class CompareSharedRunUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    suspend operator fun invoke(code: String): SharedRunComparisonModel =
        repository.compareSharedCode(code)
}
