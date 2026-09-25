package com.runcoach.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.domain.history.ShareRunPayload
import com.runcoach.app.domain.history.SharedRunComparisonModel
import com.runcoach.app.domain.history.usecase.CompareSharedRunUseCase
import com.runcoach.app.domain.history.usecase.GetSamsungHealthSetupMessageUseCase
import com.runcoach.app.domain.history.usecase.ImportSamsungHealthRunsUseCase
import com.runcoach.app.domain.history.usecase.ObserveHistoryUseCase
import com.runcoach.app.domain.history.usecase.ShareRunRecordUseCase
import com.runcoach.app.domain.history.usecase.SyncHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComparisonUiState(
    val remote: SharedRunComparisonModel,
    val localRecord: RunningRecord?,
    val distanceDeltaKm: Float,
    val paceDeltaSec: Int
)

data class HistoryActionState(
    val isBusy: Boolean = false,
    val statusMessage: String = "",
    val shareDialog: ShareRunPayload? = null,
    val comparison: ComparisonUiState? = null
)

data class HistoryUiState(
    val records: List<RunningRecord> = emptyList(),
    val actionState: HistoryActionState = HistoryActionState()
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    observeHistoryUseCase: ObserveHistoryUseCase,
    private val getSamsungHealthSetupMessageUseCase: GetSamsungHealthSetupMessageUseCase,
    private val importSamsungHealthRunsUseCase: ImportSamsungHealthRunsUseCase,
    private val syncHistoryUseCase: SyncHistoryUseCase,
    private val shareRunRecordUseCase: ShareRunRecordUseCase,
    private val compareSharedRunUseCase: CompareSharedRunUseCase
) : ViewModel() {

    private val actions = MutableStateFlow(HistoryActionState())

    val uiState: StateFlow<HistoryUiState> = combine(
        observeHistoryUseCase(),
        actions
    ) { records, actionState ->
        HistoryUiState(records = records, actionState = actionState)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HistoryUiState()
    )

    fun getSamsungHealthSetupMessage(): String? = getSamsungHealthSetupMessageUseCase()

    fun importFromSamsungHealth() {
        viewModelScope.launch {
            actions.value = actions.value.copy(
                isBusy = true,
                statusMessage = "삼성헬스 러닝 기록을 가져오는 중입니다."
            )
            runCatching {
                importSamsungHealthRunsUseCase()
            }.onSuccess { result ->
                actions.value = actions.value.copy(
                    isBusy = false,
                    statusMessage = "가져오기 완료: ${result.importedCount}건 추가, ${result.skippedCount}건 건너뜀"
                )
            }.onFailure { error ->
                actions.value = actions.value.copy(
                    isBusy = false,
                    statusMessage = error.message ?: "삼성헬스 기록을 가져오지 못했습니다."
                )
            }
        }
    }

    fun syncToSupabase() {
        viewModelScope.launch {
            actions.value = actions.value.copy(
                isBusy = true,
                statusMessage = "Supabase로 동기화하는 중입니다."
            )
            runCatching {
                syncHistoryUseCase()
            }.onSuccess { result ->
                actions.value = actions.value.copy(
                    isBusy = false,
                    statusMessage = result.message
                )
            }.onFailure { error ->
                actions.value = actions.value.copy(
                    isBusy = false,
                    statusMessage = error.message ?: "Supabase 동기화에 실패했습니다."
                )
            }
        }
    }

    fun shareRecord(recordId: Long) {
        viewModelScope.launch {
            actions.value = actions.value.copy(
                isBusy = true,
                statusMessage = "QR 공유 정보를 준비하는 중입니다."
            )
            runCatching {
                shareRunRecordUseCase(recordId)
            }.onSuccess { share ->
                actions.value = actions.value.copy(
                    isBusy = false,
                    statusMessage = "QR 공유 코드를 만들었습니다.",
                    shareDialog = share
                )
            }.onFailure { error ->
                actions.value = actions.value.copy(
                    isBusy = false,
                    statusMessage = error.message ?: "공유 QR 생성에 실패했습니다."
                )
            }
        }
    }

    fun compareWithSharedCode(code: String) {
        viewModelScope.launch {
            if (code.isBlank()) {
                actions.value = actions.value.copy(statusMessage = "공유 코드를 입력해 주세요.")
                return@launch
            }

            actions.value = actions.value.copy(
                isBusy = true,
                statusMessage = "공유 기록을 불러오는 중입니다."
            )
            runCatching {
                compareSharedRunUseCase(code)
            }.onSuccess { comparison ->
                actions.value = actions.value.copy(
                    isBusy = false,
                    statusMessage = "공유 기록을 불러왔습니다.",
                    comparison = ComparisonUiState(
                        remote = comparison,
                        localRecord = comparison.localRecord,
                        distanceDeltaKm = comparison.distanceDeltaKm,
                        paceDeltaSec = comparison.paceDeltaSec
                    )
                )
            }.onFailure { error ->
                actions.value = actions.value.copy(
                    isBusy = false,
                    statusMessage = error.message ?: "공유 기록 비교에 실패했습니다."
                )
            }
        }
    }

    fun dismissShareDialog() {
        actions.value = actions.value.copy(shareDialog = null)
    }
}
