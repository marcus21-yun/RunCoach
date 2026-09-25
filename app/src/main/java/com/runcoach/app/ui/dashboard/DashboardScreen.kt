package com.runcoach.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    onRecordClick: (Long) -> Unit,
    onHistory: () -> Unit,
    onStats: () -> Unit,
    onPrediction: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("RunCoach", color = Color(0xFF4FC3F7), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPrediction() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("하프마라톤 진행률", color = Color.White.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(6.dp))
                Text("현재 최고 ${state.currentMaxKm}km", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("예상 달성 ${state.estimatedWeeks}주 후 · D-${state.daysLeft}", color = Color(0xFFB3E5FC))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) {
                Text("러닝 기록")
            }
            OutlinedButton(onClick = onStats, modifier = Modifier.weight(1f)) {
                Text("주간 통계")
            }
            OutlinedButton(
                onClick = onPrediction,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFD54F))
            ) {
                Text("예측")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("이번 달 요약", color = Color(0xFF4FC3F7), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))
                SummaryRow("총 거리", "${state.monthlyKm}km")
                SummaryRow("러닝 횟수", "${state.monthlyCount}회")
                SummaryRow("평균 페이스", state.monthlyAvgPace)
                SummaryRow("평균 심박수", "${state.monthlyAvgHr}bpm")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("최근 기록", color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (state.recentRecords.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("아직 저장된 러닝 기록이 없습니다.", color = Color.Gray)
            }
        } else {
            state.recentRecords.forEach { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onRecordClick(record.id) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(formatDate(record.date), color = Color.Gray, fontSize = 12.sp)
                            Text(
                                "${String.format(Locale.US, "%.1f", record.distanceKm)}km",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("${record.avgPace}/km", color = Color(0xFFB3E5FC))
                        Text(if (record.completed) "완주" else "미완주", color = Color.LightGray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("M월 d일 (E)", Locale.KOREAN).format(Date(timestamp))
