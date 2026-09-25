package com.runcoach.app.ui.home

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
fun HomeScreen(
    navController: NavController,
    vm: HomeViewModel = hiltViewModel()
) {
    val goal by vm.currentGoal.collectAsState()
    val records by vm.lastRecord.collectAsState()
    val lastRecord = records.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(20.dp)
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🏃 RunCoach", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = { /* 설정 */ }) {
                Text("⚙", fontSize = 20.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 하프마라톤 D-day 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("하프마라톤까지", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "D - ${vm.daysLeft} 일",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4FC3F7)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = vm.progressPct(records),
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Color(0xFF4FC3F7),
                    trackColor = Color(0xFF333355)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "목표: 21.1km  현재: %.1fkm".format(
                        records.maxOfOrNull { it.distanceKm } ?: 0f
                    ),
                    fontSize = 11.sp, color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 이번 주 목표 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2E1E))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("이번 주 목표", fontSize = 13.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                if (goal != null) {
                    GoalRow(icon = "📍", label = "거리", value = "%.1f km".format(goal!!.targetKm))
                    GoalRow(icon = "⚡", label = "페이스", value = "${goal!!.targetPace} /km")
                    GoalRow(icon = "📅", label = "일정", value = "매주 일요일")
                } else {
                    Text("목표를 설정해주세요", color = Color.Gray, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 지난 러닝 요약
        if (lastRecord != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("지난 러닝", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        "%.1fkm  ${lastRecord.avgPace}/km  ${if (lastRecord.completed) "✅" else "❌"}".format(
                            lastRecord.distanceKm
                        ),
                        fontSize = 13.sp, color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 하단 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { navController.navigate("prerun") },
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7))
            ) {
                Text("🏃 러닝 시작", fontWeight = FontWeight.Bold, color = Color.Black)
            }
            OutlinedButton(
                onClick = { navController.navigate("history") },
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text("📊 기록 보기", color = Color.White)
            }
        }
    }
}

@Composable
private fun GoalRow(icon: String, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$icon $label", fontSize = 13.sp, color = Color.LightGray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}
