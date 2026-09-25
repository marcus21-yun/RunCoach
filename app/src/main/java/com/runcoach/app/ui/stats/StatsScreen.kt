package com.runcoach.app.ui.stats

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

// 주간/월간 통계 그래프 화면 (폰)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

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
            Text("통계", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            // 주간/월간 탭
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.width(120.dp),
                containerColor = Color(0xFF1A1A1A),
                contentColor = Color(0xFF4FC3F7)
            ) {
                Tab(selected = selectedTab == 0, onClick = {
                    selectedTab = 0
                    viewModel.setMode(StatsMode.WEEKLY)
                }) {
                    Text("주간", fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
                Tab(selected = selectedTab == 1, onClick = {
                    selectedTab = 1
                    viewModel.setMode(StatsMode.MONTHLY)
                }) {
                    Text("월간", fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            // 이번 기간 요약
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("이번 ${if (selectedTab == 0) "주" else "달"} 요약",
                        color = Color(0xFF4FC3F7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryItem("총 거리", "${state.totalKm}km")
                        SummaryItem("횟수", "${state.runCount}회")
                        SummaryItem("평균 페이스", state.avgPace)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 거리 추이 그래프
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("거리 추이 (km)", color = Color(0xFF4FC3F7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    SimpleBarChart(
                        data = state.distanceData,
                        labels = state.labels,
                        color = Color(0xFF4FC3F7)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 페이스 추이
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("페이스 추이 (/km)", color = Color(0xFF4FC3F7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    state.paceData.forEachIndexed { i, pace ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(state.labels.getOrElse(i) { "" }, color = Color(0xFF888888), fontSize = 12.sp)
                            Text(pace, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 평균 심박수 추이
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("평균 심박수 추이", color = Color(0xFF4FC3F7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        state.hrData.forEachIndexed { i, hr ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$hr", color = Color(0xFFFF7043), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(state.labels.getOrElse(i) { "" }, color = Color(0xFF888888), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFF888888), fontSize = 11.sp)
    }
}

// 간단한 막대 그래프
@Composable
fun SimpleBarChart(data: List<Float>, labels: List<String>, color: Color) {
    val maxVal = data.maxOrNull() ?: 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { i, value ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "%.1f".format(value),
                    color = Color(0xFF888888),
                    fontSize = 8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .fillMaxHeight((value / maxVal).coerceIn(0.05f, 1f))
                        .background(color)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    labels.getOrElse(i) { "" },
                    color = Color(0xFF666666),
                    fontSize = 8.sp
                )
            }
        }
    }
}
