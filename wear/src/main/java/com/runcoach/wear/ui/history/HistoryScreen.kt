package com.runcoach.wear.ui.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.CardDefaults
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.OutlinedButton
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val shareQrBitmap = state.shareQrBitmap

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactButton(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)
                ) {
                    Text("뒤로", color = Color(0xFF888888), fontSize = 12.sp)
                }
                Text(
                    "러닝 기록",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                backgroundPainter = CardDefaults.cardBackgroundPainter(
                    startBackgroundColor = Color(0xFF0D1B4B),
                    endBackgroundColor = Color(0xFF0D1B4B)
                )
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("하프마라톤까지", color = Color(0xFF888888), fontSize = 10.sp)
                    Text(
                        "${state.estimatedWeeks}주 예상",
                        color = Color(0xFF4FC3F7),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ProgressBar(
                        progress = state.progressPct,
                        modifier = Modifier.fillMaxWidth(0.9f),
                        indicatorColor = Color(0xFF4FC3F7),
                        trackColor = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${(state.progressPct * 100).toInt()}% 달성",
                        color = Color(0xFF666666),
                        fontSize = 9.sp
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.syncHistory() },
                enabled = !state.isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.isSyncing) "동기화 중" else "Supabase 동기화",
                    fontSize = 10.sp,
                    color = Color(0xFF4FC3F7)
                )
            }
        }

        state.syncMessage?.let { message ->
            item {
                Text(message, color = Color(0xFF9AD7FF), fontSize = 9.sp)
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.shareLatestRecord() },
                enabled = !state.isSharing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.isSharing) "QR 생성 중" else "최신 기록 QR 공유",
                    fontSize = 10.sp,
                    color = Color(0xFF4FC3F7)
                )
            }
        }

        state.shareMessage?.let { message ->
            item {
                Text(message, color = Color(0xFF9AD7FF), fontSize = 9.sp)
            }
        }

        if (shareQrBitmap != null) {
            item {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                        startBackgroundColor = Color(0xFF1A1A1A),
                        endBackgroundColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "공유 코드 ${state.shareCode.orEmpty()}",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Image(
                            bitmap = shareQrBitmap.asImageBitmap(),
                            contentDescription = "공유 QR",
                            modifier = Modifier.size(120.dp)
                        )
                    }
                }
            }
        }

        state.latestBriefing?.let { briefing ->
            item {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                        startBackgroundColor = Color(0xFF1A1A1A),
                        endBackgroundColor = Color(0xFF1A1A1A)
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("최근 브리핑", color = Color(0xFF4FC3F7), fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            briefing.content.take(120),
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        items(state.records.size) { index ->
            val record = state.records[index]
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                backgroundPainter = CardDefaults.cardBackgroundPainter(
                    startBackgroundColor = Color(0xFF1A1A1A),
                    endBackgroundColor = Color(0xFF1A1A1A)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            formatDate(record.date),
                            color = Color(0xFF888888),
                            fontSize = 9.sp
                        )
                        Text(
                            "%.1fkm".format(record.distanceKm),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            record.avgPace,
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp
                        )
                        Text(
                            if (record.completed) "완주" else "미완주",
                            fontSize = 10.sp,
                            color = if (record.completed) Color(0xFF4FC3F7) else Color(0xFF888888)
                        )
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.requestSamsungHealthImportToPhone() },
                enabled = !state.isRequestingPhoneImport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (state.isRequestingPhoneImport) "폰에 요청 중" else "삼성헬스 가져오기",
                    fontSize = 10.sp,
                    color = Color(0xFF4FC3F7)
                )
            }
        }

        state.phoneRequestMessage?.let { message ->
            item {
                Text(message, color = Color(0xFF9AD7FF), fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    indicatorColor: Color,
    trackColor: Color
) {
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(indicatorColor)
        )
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("M/d (E)", Locale.KOREAN)
    return sdf.format(Date(timestamp))
}
