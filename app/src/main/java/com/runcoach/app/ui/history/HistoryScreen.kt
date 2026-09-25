package com.runcoach.app.ui.history

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.runcoach.app.data.db.RunningRecord
import com.runcoach.app.data.health.SamsungHealthImportManager
import com.runcoach.app.domain.history.ShareRunPayload
import com.runcoach.app.util.QrCodeGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    navController: NavController,
    initialShareCode: String? = null,
    autoImportOnStart: Boolean = false,
    onAutoImportHandled: () -> Unit = {},
    vm: HistoryViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val activity = context as? Activity
    val setupMessage = vm.getSamsungHealthSetupMessage()
    var shareCode by rememberSaveable(initialShareCode) { mutableStateOf(initialShareCode.orEmpty()) }

    LaunchedEffect(initialShareCode) {
        val code = initialShareCode?.trim().orEmpty()
        if (code.isNotBlank()) {
            shareCode = code
            vm.compareWithSharedCode(code)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(SamsungHealthImportManager.requiredPermissions)) {
            vm.importFromSamsungHealth()
        } else {
            Toast.makeText(context, "Health Connect 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestSamsungHealthImport() {
        if (!setupMessage.isNullOrBlank()) {
            Toast.makeText(context, setupMessage, Toast.LENGTH_LONG).show()
            return
        }
        permissionLauncher.launch(SamsungHealthImportManager.requiredPermissions)
    }

    LaunchedEffect(autoImportOnStart) {
        if (autoImportOnStart) {
            onAutoImportHandled()
            requestSamsungHealthImport()
        }
    }

    LaunchedEffect(state.actionState.statusMessage) {
        if (state.actionState.statusMessage.isNotBlank()) {
            Toast.makeText(context, state.actionState.statusMessage, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Text("러닝 기록", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            SetupGuideCard(message = setupMessage)

            Spacer(modifier = Modifier.height(12.dp))

            ActionPanel(
                shareCode = shareCode,
                onShareCodeChange = { shareCode = it },
                onImportClick = ::requestSamsungHealthImport,
                onSyncClick = vm::syncToSupabase,
                onScanClick = {
                    if (activity == null) {
                        Toast.makeText(context, "이 화면에서는 QR 스캔을 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        startQrScan(
                            activity = activity,
                            onCodeScanned = { raw ->
                                val parsed = parseShareCode(raw)
                                if (parsed.isNullOrBlank()) {
                                    Toast.makeText(context, "런코치 공유 QR 형식이 아닙니다.", Toast.LENGTH_SHORT).show()
                                } else {
                                    shareCode = parsed
                                    vm.compareWithSharedCode(parsed)
                                }
                            },
                            onError = { message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                onCompareClick = { vm.compareWithSharedCode(shareCode) }
            )

            state.actionState.comparison?.let {
                Spacer(modifier = Modifier.height(12.dp))
                ComparisonCard(it)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.records.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("아직 저장된 러닝 기록이 없습니다.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.records, key = { it.id }) { record ->
                        RecordItem(
                            record = record,
                            onShare = { vm.shareRecord(record.id) },
                            onCopyCode = {
                                clipboard.setText(AnnotatedString(record.externalId ?: "local-${record.id}"))
                            }
                        )
                    }
                }
            }
        }

        if (state.actionState.isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF4FC3F7)
            )
        }
    }

    state.actionState.shareDialog?.let { share ->
        ShareQrDialog(share = share, onDismiss = vm::dismissShareDialog)
    }
}

@Composable
private fun SetupGuideCard(message: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2432))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("삼성헬스 가져오기 안내", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "1. 휴대폰의 삼성헬스에서 Health Connect 연동을 켭니다.\n2. RunCoach에 Health Connect 읽기 권한을 허용합니다.\n3. 아래 버튼으로 최근 러닝 기록을 가져옵니다.",
                color = Color(0xFFCFD8DC),
                fontSize = 12.sp
            )
            if (!message.isNullOrBlank()) {
                Text(message, color = Color(0xFFFFCC80), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ActionPanel(
    shareCode: String,
    onShareCodeChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onSyncClick: () -> Unit,
    onScanClick: () -> Unit,
    onCompareClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Samsung Health / Supabase", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImportClick, modifier = Modifier.weight(1f)) {
                    Text("삼성헬스 가져오기")
                }
                OutlinedButton(onClick = onSyncClick, modifier = Modifier.weight(1f)) {
                    Text("Supabase 동기화")
                }
            }
            OutlinedTextField(
                value = shareCode,
                onValueChange = onShareCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("상대방 공유 코드") },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScanClick, modifier = Modifier.weight(1f)) {
                    Text("QR 스캔")
                }
                Button(onClick = onCompareClick, modifier = Modifier.weight(1f)) {
                    Text("기록 비교")
                }
            }
        }
    }
}

@Composable
private fun ComparisonCard(comparison: ComparisonUiState) {
    val paceLabel = if (comparison.paceDeltaSec <= 0) {
        "${kotlin.math.abs(comparison.paceDeltaSec)}초 더 빠름"
    } else {
        "${comparison.paceDeltaSec}초 더 느림"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16212F))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("상대 러닝 비교", color = Color.White, fontWeight = FontWeight.Bold)
            Text("공유 코드: ${comparison.remote.shareCode}", color = Color(0xFF9FD3FF), fontSize = 12.sp)
            Text(
                "상대 기록 ${String.format(Locale.US, "%.1f", comparison.remote.distanceKm)}km / ${comparison.remote.avgPace}",
                color = Color.LightGray
            )
            Text(
                "내 최신 기록 대비 거리 차이 ${String.format(Locale.US, "%.1f", comparison.distanceDeltaKm)}km, 페이스 차이 $paceLabel",
                color = Color.White
            )
            if (comparison.remote.briefing.isNotBlank()) {
                Text(comparison.remote.briefing, color = Color(0xFFB0BEC5), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RecordItem(
    record: RunningRecord,
    onShare: () -> Unit,
    onCopyCode: () -> Unit
) {
    val dateStr = remember(record.date) {
        SimpleDateFormat("M/dd HH:mm", Locale.KOREA).format(Date(record.date))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(dateStr, color = Color.Gray, fontSize = 12.sp)
                    Text(
                        "${String.format(Locale.US, "%.1f", record.distanceKm)} km",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${record.avgPace}/km", color = Color(0xFF9FD3FF), fontWeight = FontWeight.SemiBold)
                    Text(record.source.replace('_', ' '), color = Color.Gray, fontSize = 12.sp)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "평균 ${record.avgHeartRate}bpm · ${record.durationSec / 60}분",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onCopyCode) {
                        Text("ID 복사")
                    }
                    Button(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("QR 공유")
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareQrDialog(
    share: ShareRunPayload,
    onDismiss: () -> Unit
) {
    val qrGenerator = remember { QrCodeGenerator() }
    val bitmap = remember(share.qrPayload) { qrGenerator.generate(share.qrPayload, size = 720) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        title = { Text("러닝 기록 QR 공유") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(220.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("공유 코드: ${share.code}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${String.format(Locale.US, "%.1f", share.distanceKm)}km · ${share.avgPace}/km",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                if (share.briefing.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(share.briefing, color = Color.DarkGray, fontSize = 12.sp)
                }
            }
        }
    )
}

private fun startQrScan(
    activity: Activity,
    onCodeScanned: (String) -> Unit,
    onError: (String) -> Unit
): Task<Barcode> {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()

    return GmsBarcodeScanning.getClient(activity, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val rawValue = barcode.rawValue
            if (rawValue.isNullOrBlank()) {
                onError("QR 코드 내용을 읽지 못했습니다.")
            } else {
                onCodeScanned(rawValue)
            }
        }
        .addOnFailureListener { error ->
            onError(error.message ?: "QR 스캔에 실패했습니다.")
        }
        .addOnCanceledListener {
            onError("QR 스캔을 취소했습니다.")
        }
}

private fun parseShareCode(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.startsWith("runcoach://")) {
        return runCatching { Uri.parse(trimmed).getQueryParameter("code") }.getOrNull()
    }
    return trimmed
}
