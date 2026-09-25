package com.runcoach.app.domain.history.usecase

import com.runcoach.app.domain.history.HistoryRepository
import javax.inject.Inject

class GetSamsungHealthSetupMessageUseCase @Inject constructor(
    private val repository: HistoryRepository
) {
    operator fun invoke(): String? = repository.getSamsungHealthSetupMessage()
}
