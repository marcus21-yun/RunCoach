package com.runcoach.wear.ui.goal

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

@Composable
fun AiGoalScreen(
    onAccept: () -> Unit,
    onBack: () -> Unit,
    viewModel: AiGoalViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp)
    ) {
        // 헤더
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactButton(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)
                ) {
                    Text("←", color = Color(0xFF888888), fontSize = 14.sp)
                }
                Text(
                    "다음 주 목표",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // AI 분석 메시지
        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                backgroundPainter = CardDefaults.cardBackgroundPainter(
                    startBackgroundColor = Color(0xFF1A1A2E),
                    endBackgroundColor = Color(0xFF1A1A2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🤖", fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.aiMessage,
                        color = Color(0xFFCCCCCC),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 제안 근거 — 저장된 기록과 사용자 피드백에서 나온 사실만 표시
        if (state.reasons.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("이렇게 판단했어요", color = Color(0xFF4FC3F7), fontSize = 12.sp)
                    state.reasons.forEach { reason ->
                        Text(
                            "· $reason",
                            color = Color(0xFFE9ECF1),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // 다음 주 제안 목표
        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                backgroundPainter = CardDefaults.cardBackgroundPainter(
                    startBackgroundColor = Color(0xFF0D2137),
                    endBackgroundColor = Color(0xFF0D2137)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("다음 주 제안", color = Color(0xFF4FC3F7), fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    GoalRow("거리", "${state.nextKm}km")
                    GoalRow("페이스", "${state.nextPace}/km")
                    GoalRow("스피드업", "${state.nextSpeedupKm}km 이후 +${state.nextSpeedupPct}%")
                    GoalRow("심박 경고", "${state.nextHrAlert}bpm")
                }
            }
        }

        // 수락 / 뒤로 버튼
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Text("← 수정", fontSize = 10.sp, color = Color(0xFF888888))
                }
                Button(
                    onClick = {
                        viewModel.acceptGoal()
                        onAccept()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
                ) {
                    Text("✅ 수락", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun GoalRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF888888), fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
