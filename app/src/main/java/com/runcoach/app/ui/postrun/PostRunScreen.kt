package com.runcoach.app.ui.postrun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun PostRunScreen(
    navController: NavController,
    vm: PostRunViewModel = hiltViewModel()
) {
    val records by vm.records.collectAsState()
    val goal by vm.currentGoal.collectAsState()

    // 오늘 기록 (가장 최근)
    val today = records.firstOrNull()
    // 지난주 기록 (두 번째)
    val prev = records.getOrNull(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(20.dp)
    ) {
        // 헤더
        Text(
            "🎉 오늘 러닝 완료!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 오늘 기록 카드
        if (today != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("오늘 기록", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    ResultRow("거리", "%.1f km".format(today.distanceKm),
                        if (today.completed) "✅" else "❌")
                    ResultRow("페이스", "${today.avgPace} /km",
                        if (prev != null && today.avgPace < prev.avgPace) "↑" else "")
                    ResultRow("시간", formatDuration(today.durationSec), "")
                    ResultRow("심박수(평균)", "${today.avgHeartRate} bpm", "")
                    ResultRow("피로도", fatigueName(today.fatigueLevel), fatigueEmoji(today.fatigueLevel))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 지난주 대비 카드
            if (prev != null) {
                val diffKm = today.distanceKm - prev.distanceKm
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2E1E))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("지난주 대비", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            DiffChip(
                                label = "거리",
                                value = "%+.1f km".format(diffKm),
                                positive = diffKm >= 0
                            )
                            DiffChip(
                                label = "페이스",
                                value = if (today.avgPace < prev.avgPace) "빨라짐 ↑" else "느려짐 ↓",
                                positive = today.avgPace < prev.avgPace
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // AI 다음 목표 버튼
        Button(
            onClick = { navController.navigate("aigoal") },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2))
        ) {
            Text("🤖 AI 다음 목표 제안 보기 →", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                modifier = Modifier.weight(1f)
            ) {
                Text("홈으로", color = Color.White)
            }
            OutlinedButton(
                onClick = { navController.navigate("history") },
                modifier = Modifier.weight(1f)
            ) {
                Text("기록 보기", color = Color.White)
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, badge: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = Color.LightGray)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
            if (badge.isNotEmpty()) Text(badge, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DiffChip(label: String, value: String, positive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (positive) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

private fun fatigueName(level: String) = when (level) {
    "low"  -> "낮음"
    "mid"  -> "보통"
    "high" -> "높음"
    else   -> level
}

private fun fatigueEmoji(level: String) = when (level) {
    "low"  -> "🟢"
    "mid"  -> "🟡"
    "high" -> "🔴"
    else   -> ""
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "${m}분 ${s.toString().padStart(2, '0')}초"
}
