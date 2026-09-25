package com.runcoach.app.ui.prediction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

// 하프마라톤 예측 화면 (폰)
@Composable
fun PredictionScreen(
    onBack: () -> Unit,
    viewModel: PredictionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .verticalScroll(rememberScrollState())
    ) {
        // 헤더
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            IconButton(onClick = onBack) {
                Text("←", color = Color.White, fontSize = 20.sp)
            }
            Text("하프마라톤 예측", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            // 메인 예측 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("목표: 21.0975 km", color = Color.White.copy(0.7f), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        state.estimatedWeeks,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("후 달성 예상", color = Color.White.copy(0.8f), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PredictItem("예상 완주 시간", state.estimatedFinishTime)
                        PredictItem("현재 페이스", state.currentPace)
                        PredictItem("현재 최고 거리", "${state.currentMaxKm}km")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 주차별 목표 거리 로드맵
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "주차별 목표 거리 로드맵",
                        color = Color(0xFF4FC3F7),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    state.roadmap.forEach { milestone ->
                        RoadmapRow(
                            week = milestone.week,
                            km = milestone.km,
                            isCurrentOrPast = milestone.isPastOrCurrent,
                            isTarget = milestone.isTarget
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 동기부여 메시지
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A1A))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.motivationMessage, color = Color(0xFF4CAF50), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "매주 꾸준히 뛰면 반드시 완주할 수 있어요 💪",
                        color = Color(0xFF888888),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PredictItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(0.6f), fontSize = 10.sp)
    }
}

@Composable
fun RoadmapRow(week: String, km: Float, isCurrentOrPast: Boolean, isTarget: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            week,
            color = if (isCurrentOrPast) Color.White else Color(0xFF555555),
            fontSize = 13.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = (km / 21.0975f).coerceIn(0f, 1f),
                modifier = Modifier
                    .width(100.dp)
                    .height(6.dp),
                color = when {
                    isTarget          -> Color(0xFFFFD700)
                    isCurrentOrPast   -> Color(0xFF4FC3F7)
                    else              -> Color(0xFF333333)
                },
                trackColor = Color(0xFF222222)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "%.1fkm".format(km),
                color = if (isTarget) Color(0xFFFFD700) else Color.White,
                fontSize = 13.sp,
                fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal
            )
            if (isTarget) Text(" ⭐", fontSize = 13.sp)
        }
    }
}
