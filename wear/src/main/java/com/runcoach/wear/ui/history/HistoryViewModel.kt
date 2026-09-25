package com.runcoach.wear.ui.history

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import com.runcoach.wear.data.BriefingRecord
import com.runcoach.wear.data.CachedRecord
import com.runcoach.wear.data.SupabaseSyncService
import com.runcoach.wear.data.WearDataStore
import com.runcoach.wear.util.QrCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class HistoryUiState(
    val records: List<CachedRecord> = emptyList(),
    val latestBriefing: BriefingRecord? = null,
    val estimatedWeeks: Int = 0,
    val progressPct: Float = 0f,
    val syncMessage: String? = null,
    val shareCode: String? = null,
    val shareQrBitmap: Bitmap? = null,
    val shareMessage: String? = null,
    val phoneRequestMessage: String? = null,
    val isSyncing: Boolean = false,
    val isSharing: Boolean = false,
    val isRequestingPhoneImport: Boolean = false
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: WearDataStore,
    private val supabaseSyncService: SupabaseSyncService,
    private val qrCodeGenerator: QrCodeGenerator
) : ViewModel() {

    companion object {
        private const val TAG = "RunCoachWear"
        private const val PATH_IMPORT_SAMSUNG_HEALTH = "/history/import_samsung_health"
        private const val PATH_OWNER_REQUEST = "/owner/request"
    }

    private val actions = MutableStateFlow(HistoryUiState())

    val uiState: StateFlow<HistoryUiState> = combine(
        dataStore.getCachedData(),
        actions
    ) { cache, actionState ->
        val maxKm = (cache.recentRecords.maxOfOrNull { it.distanceKm } ?: 0f)
            .takeIf { it.isFinite() && it >= 0f } ?: 0f
        val weeksLeft = if (maxKm > 0f) {
            ((21.0975f - maxKm) / 0.5f).toInt().coerceAtLeast(1)
        } else {
            52
        }
        val progressPct = ((maxKm / 21.0975f).takeIf { it.isFinite() } ?: 0f)
            .coerceIn(0f, 1f)

        actionState.copy(
            records = cache.recentRecords,
            latestBriefing = cache.recentBriefings.firstOrNull(),
            estimatedWeeks = weeksLeft,
            progressPct = progressPct,
            shareCode = actionState.shareCode ?: cache.lastShareCode,
            phoneRequestMessage = actionState.phoneRequestMessage ?: cache.phoneImportStatus
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun requestSamsungHealthImportToPhone() {
        viewModelScope.launch {
            ensureOwnerIdFromPhone()
            dataStore.clearPhoneImportStatus()
            actions.update {
                it.copy(
                    isRequestingPhoneImport = true,
                    phoneRequestMessage = null
                )
            }
            runCatching {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                val node = nodes.firstOrNull() ?: error("연결된 휴대폰을 찾지 못했습니다.")
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, PATH_IMPORT_SAMSUNG_HEALTH, ByteArray(0))
                    .await()
            }.onSuccess {
                actions.update {
                    it.copy(
                        isRequestingPhoneImport = false,
                        phoneRequestMessage = "휴대폰에 요청을 보냈습니다. 잠시 후 상태를 확인해 주세요."
                    )
                }
            }.onFailure { error ->
                Log.e(TAG, "requestSamsungHealthImportToPhone failed", error)
                actions.update {
                    it.copy(
                        isRequestingPhoneImport = false,
                        phoneRequestMessage = error.message ?: "휴대폰 요청에 실패했습니다."
                    )
                }
            }
        }
    }

    fun syncHistory() {
        viewModelScope.launch {
            actions.update { it.copy(isSyncing = true, syncMessage = null) }
            runCatching {
                ensureOwnerIdFromPhone()
                val cache = dataStore.getCachedData().first()
                Log.d(TAG, "syncHistory records=${cache.recentRecords.size}, briefings=${cache.recentBriefings.size}")
                supabaseSyncService.syncHistory(cache)
            }.onSuccess {
                actions.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "Supabase 동기화 완료"
                    )
                }
            }.onFailure { error ->
                actions.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = error.message ?: "동기화에 실패했습니다."
                    )
                }
            }
        }
    }

    fun shareLatestRecord() {
        viewModelScope.launch {
            actions.update { it.copy(isSharing = true, shareMessage = null) }
            runCatching {
                ensureOwnerIdFromPhone()
                val record = dataStore.getLastRecord() ?: error("공유할 러닝 기록이 없습니다.")
                Log.d(TAG, "shareLatestRecord id=${record.id} distance=${record.distanceKm}")
                val briefing = dataStore.getBriefingsSync().firstOrNull { it.recordId == record.id }
                val result = supabaseSyncService.createShare(record, briefing)
                dataStore.saveShareInfo(result.code, result.payload)
                val qrBitmap = qrCodeGenerator.generate(result.payload)
                Triple(record, result.code, qrBitmap)
            }.onSuccess { (_, code, qrBitmap) ->
                actions.update {
                    it.copy(
                        isSharing = false,
                        shareCode = code,
                        shareQrBitmap = qrBitmap,
                        shareMessage = "QR 공유 준비 완료"
                    )
                }
            }.onFailure { error ->
                actions.update {
                    it.copy(
                        isSharing = false,
                        shareMessage = error.message ?: "공유에 실패했습니다."
                    )
                }
            }
        }
    }

    private suspend fun ensureOwnerIdFromPhone() {
        val cache = dataStore.getCachedData().first()
        if (!cache.ownerId.isNullOrBlank()) return

        val node = Wearable.getNodeClient(context).connectedNodes.await().firstOrNull() ?: return
        Wearable.getMessageClient(context)
            .sendMessage(node.id, PATH_OWNER_REQUEST, ByteArray(0))
            .await()
    }
}
