package com.runcoach.app.ui.detail

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

// 1회 러닝 상세 분석 화면 (폰)
@Composable
fun RunDetailScreen(
    recordId: Long,
    onBack: () -> Unit,
    viewModel: RunDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(recordId) { viewModel.load(recordId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 헤더
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Text("←", color = Color.White, fontSize = 20.sp)
            }
            Text(
                text = state.dateLabel,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 기본 정보 카드
        SectionCard(title = "기본 정보") {
            InfoRow("거리", "${state.distanceKm} km")
            InfoRow("완주", if (state.completed) "✅ 완주" else "❌ 미완주")
            InfoRow("시간", state.duration)
            InfoRow("평균 페이스", "${state.avgPace} /km")
            InfoRow("칼로리", "${state.calorie} kcal")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 심박수 분석 카드
        SectionCard(title = "심박수 분석") {
            InfoRow("평균 심박수", "${state.avgHr} bpm")
            InfoRow("최고 심박수", "${state.maxHr} bpm")
            Spacer(modifier = Modifier.height(8.dp))
            Text("심박 존 분포", color = Color(0xFF888888), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            HeartRateZoneBar("Zone 1 (저강도)", state.zone1Pct, Color(0xFF4CAF50))
            HeartRateZoneBar("Zone 2 (유산소)", state.zone2Pct, Color(0xFF8BC34A))
            HeartRateZoneBar("Zone 3 (유산소고강도)", state.zone3Pct, Color(0xFFFFAB40))
            HeartRateZoneBar("Zone 4 (무산소)", state.zone4Pct, Color(0xFFFF5252))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 구간별 페이스 카드
        SectionCard(title = "구간별 페이스") {
            state.splitPaces.forEachIndexed { index, pace ->
                SplitPaceRow(
                    km = index + 1,
                    pace = pace,
                    targetPace = state.targetPace,
                    isSpeedupZone = index + 1 >= state.speedupKm.toInt()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 지난주 대비 카드
        SectionCard(title = "지난주 대비") {
            CompareRow("거리", state.diffDistanceLabel, state.diffDistancePositive)
            CompareRow("페이스", state.diffPaceLabel, state.diffPacePositive)
            CompareRow("심박수", state.diffHrLabel, state.diffHrPositive)
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color(0xFF4FC3F7), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF888888), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun HeartRateZoneBar(label: String, pct: Float, color: Color) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color(0xFF888888), fontSize = 11.sp)
            Text("${(pct * 100).toInt()}%", color = color, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = pct,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = color,
            trackColor = Color(0xFF333333)
        )
    }
}

@Composable
fun SplitPaceRow(km: Int, pace: String, targetPace: String, isSpeedupZone: Boolean) {
    val paceColor = if (pace <= targetPace) Color(0xFF4CAF50) else Color(0xFFFF5252)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${km}km", color = Color(0xFF888888), fontSize = 13.sp)
        Text(
            pace,
            color = paceColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (isSpeedupZone) "🚀 스피드업" else "",
            color = Color(0xFF4FC3F7),
            fontSize = 10.sp
        )
    }
}

@Composable
fun CompareRow(label: String, value: String, isPositive: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFF888888), fontSize = 13.sp)
        Text(
            value,
            color = if (isPositive) Color(0xFF4CAF50) else Color(0xFFFF5252),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
