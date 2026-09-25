package com.runcoach.app.ui.prerun

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.runcoach.app.service.RunningService

@Composable
fun PreRunScreen(
    navController: NavController,
    vm: PreRunViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val records by vm.lastRecord.collectAsState()
    val goal by vm.currentGoal.collectAsState()
    val lastRecord = records.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
            }
            Text("오늘의 러닝", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionCard(title = "지난 기록") {
            if (lastRecord != null) {
                RecordRow("거리", "%.1f km".format(lastRecord.distanceKm))
                RecordRow("페이스", "${lastRecord.avgPace} /km")
                RecordRow("심박수", "${lastRecord.avgHeartRate} bpm")
                RecordRow("시간", formatDuration(lastRecord.durationSec))
            } else {
                Text("아직 기록된 러닝이 없습니다.", color = Color.Gray, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionCard(title = "오늘 목표 (AI 제안)") {
            if (goal != null) {
                RecordRow("거리", "%.1f km".format(goal!!.targetKm), highlight = true)
                RecordRow("페이스", "${goal!!.targetPace} /km", highlight = true)
                RecordRow("스피드업", "%.0fkm 이후 +${goal!!.speedupPct}%%".format(goal!!.speedupKm), highlight = true)
            } else {
                Text("목표가 없습니다. 앱에서 먼저 설정해 주세요.", color = Color.Gray, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text("목표 수정", color = Color.White)
            }
            Button(
                onClick = {
                    context.startService(Intent(context, RunningService::class.java))
                    navController.navigate("history")
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("시작", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color.LightGray)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) Color(0xFF4FC3F7) else Color.White
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "${minutes}분 ${remainingSeconds.toString().padStart(2, '0')}초"
}
