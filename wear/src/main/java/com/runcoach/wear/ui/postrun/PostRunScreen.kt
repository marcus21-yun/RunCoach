package com.runcoach.wear.ui.postrun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.*
import com.runcoach.wear.ui.prerun.StatItem
import com.runcoach.wear.ui.theme.RcColors
import com.runcoach.wear.ui.theme.RcType

@Composable
fun PostRunScreen(
    onAiGoal: () -> Unit,
    onHome: () -> Unit,
    viewModel: PostRunViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RcColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp)
    ) {
        // 완료 헤더
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎉", fontSize = 28.sp)
                Text(
                    "러닝 완료!",
                    color = RcColors.TextPrimary,
                    style = RcType.Title,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 오늘 기록 카드
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
                    // 거리 + 완주 여부
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${state.distanceKm} km",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (state.completed) "✅" else "❌",
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("페이스", state.avgPace)
                        StatItem("시간", state.duration)
                        StatItem("심박", "${state.avgHr}bpm")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("피로도", color = Color(0xFF666666), fontSize = 10.sp)
                        Text(
                            state.fatigueLabel,
                            color = fatigueColor(state.fatigueLevel),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 지난주 대비 카드
        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                backgroundPainter = CardDefaults.cardBackgroundPainter(
                    startBackgroundColor = Color(0xFF111A11),
                    endBackgroundColor = Color(0xFF111A11)
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("지난주 대비", color = Color(0xFF888888), fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    DiffRow("거리", state.diffDistance, state.diffDistancePos)
                    DiffRow("페이스", state.diffPace, state.diffPacePos)
                }
            }
        }

        // AI 다음 목표 버튼
        item {
            Button(
                onClick = onAiGoal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4FC3F7))
            ) {
                Text("🤖 다음 목표 보기", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        // 홈으로
        item {
            OutlinedButton(
                onClick = onHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                Text("홈으로", fontSize = 10.sp, color = Color(0xFF888888))
            }
        }
    }
}

@Composable
fun DiffRow(label: String, value: String, isPositive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF888888), fontSize = 11.sp)
        Text(
            value,
            color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFFF5252),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun fatigueColor(level: String) = when (level) {
    "high" -> Color(0xFFFF5252)
    "mid"  -> Color(0xFFFFAB40)
    else   -> Color(0xFF69F0AE)
}
