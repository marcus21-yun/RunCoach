package com.runcoach.app.ui.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
fun AiGoalScreen(
    navController: NavController,
    vm: AiGoalViewModel = hiltViewModel()
) {
    val suggestion by vm.suggestion.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(20.dp)
    ) {
        // 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Text("다음 주 목표", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (suggestion != null) {
            // AI 분석 메시지 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1E2E))
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    Text("🤖 ", fontSize = 16.sp)
                    Text(
                        suggestion!!.message,
                        fontSize = 14.sp,
                        color = Color(0xFFCE93D8),
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 다음 주 제안 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("다음 주 제안", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    SuggestionRow("거리", "%.1f km".format(suggestion!!.targetKm))
                    SuggestionRow("페이스", "${suggestion!!.targetPace} /km")
                    SuggestionRow(
                        "스피드업",
                        if (suggestion!!.speedupPct > 0)
                            "%.0fkm 이후 +${suggestion!!.speedupPct}%".format(suggestion!!.speedupKm)
                        else "없음"
                    )
                    SuggestionRow("심박수 경고", "${suggestion!!.hrAlertBpm} bpm 이상")
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF7E57C2))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 하단 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { /* 직접 수정 */ },
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text("✏️ 직접 수정", color = Color.White)
            }
            Button(
                onClick = {
                    vm.acceptSuggestion()
                    navController.navigate("home") { popUpTo("home") { inclusive = true } }
                },
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E57C2))
            ) {
                Text("✅ 수락", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun SuggestionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color.LightGray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4FC3F7))
    }
}
