package com.runcoach.wear.ui.prerun

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.*
import com.runcoach.wear.ui.theme.RcColors
import com.runcoach.wear.ui.theme.RcType

@Composable
fun PreRunScreen(
    onStart: () -> Unit,
    onBack: () -> Unit,
    viewModel: PreRunViewModel = hiltViewModel()
) {
    val tag = "RunCoachWear"
    val state by viewModel.uiState.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RcColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp)
    ) {
        // 헤더
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactButton(onClick = onBack, colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)) {
                    Text("←", color = RcColors.TextSecondary, fontSize = 18.sp)
                }
                Text(
                    text = "오늘의 러닝",
                    color = RcColors.TextPrimary,
                    style = RcType.Body,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 지난주 기록
        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                backgroundPainter = CardDefaults.cardBackgroundPainter(
                    startBackgroundColor = RcColors.Surface,
                    endBackgroundColor = RcColors.Surface
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("📌 지난주", color = RcColors.TextSecondary, style = RcType.Label)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("거리", "${state.lastDistanceKm}km")
                        StatItem("페이스", state.lastPace)
                        StatItem("심박", "${state.lastAvgHr}bpm")
                    }
                }
            }
        }

        // 오늘 목표 (AI 제안)
        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                backgroundPainter = CardDefaults.cardBackgroundPainter(
                    startBackgroundColor = RcColors.SurfaceAlt,
                    endBackgroundColor = RcColors.SurfaceAlt
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎯 오늘 목표", color = RcColors.Accent, style = RcType.Label)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI 제안", color = RcColors.Accent.copy(0.7f), style = RcType.Caption)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("거리", "${state.targetKm}km", RcColors.TextPrimary)
                        StatItem("페이스", state.targetPace, RcColors.TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.speedupKm}km 이후 +${state.speedupPct}% 스피드업",
                        color = RcColors.Accent,
                        style = RcType.Label
                    )
                }
            }
        }

        // 버튼 행
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { /* 목표 수정 화면 */ },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("✏️ 수정", style = RcType.Label, color = RcColors.TextSecondary)
                }
                Button(
                    onClick = {
                        Log.d(tag, "PreRunScreen start button clicked")
                        onStart()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = RcColors.Accent)
                ) {
                    Text("시작!", style = RcType.Body, color = RcColors.Background, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, valueColor: Color = RcColors.TextBody) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, style = RcType.Body, fontWeight = FontWeight.Bold)
        Text(label, color = RcColors.TextSecondary, style = RcType.Label)
    }
}
