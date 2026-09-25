package com.runcoach.wear.ui.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.OutlinedButton
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.CardDefaults
import com.runcoach.wear.ui.theme.RcColors
import com.runcoach.wear.ui.theme.RcType

@Composable
fun HomeScreen(
    onStartRun: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val tag = "RunCoachWear"
    val state by viewModel.uiState.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(RcColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 32.dp)
    ) {
        item {
            Text(
                text = "RunCoach",
                style = RcType.Label,
                color = RcColors.Accent
            )
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "D-${state.daysLeft}일",
                    style = RcType.Title,
                    color = RcColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                ProgressBar(
                    progress = state.progressPct,
                    modifier = Modifier.fillMaxWidth(0.85f),
                    indicatorColor = RcColors.Accent,
                    trackColor = RcColors.Divider
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(state.progressPct * 100).toInt()}% / 21.1km",
                    style = RcType.Caption,
                    color = RcColors.TextSecondary
                )
            }
        }

        item {
            Card(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                backgroundPainter = CardDefaults.cardBackgroundPainter(
                    startBackgroundColor = RcColors.Surface,
                    endBackgroundColor = RcColors.SurfaceAlt
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "이번 주 목표",
                        style = RcType.Label,
                        color = RcColors.Accent
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${state.targetKm}km  ${state.targetPace}/km",
                        style = RcType.Body,
                        color = RcColors.TextBody,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "지난 기록 ${state.lastDistanceKm}km",
                        style = RcType.Label,
                        color = RcColors.TextSecondary
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    Log.d(tag, "HomeScreen start button clicked")
                    onStartRun()
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = RcColors.Accent
                )
            ) {
                Text(
                    text = "러닝 시작",
                    style = RcType.Body,
                    color = RcColors.Background,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    Log.d(tag, "HomeScreen history button clicked")
                    onHistory()
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp),
                border = ButtonDefaults.outlinedButtonBorder(
                    borderColor = RcColors.Divider
                )
            ) {
                Text(
                    text = "기록 보기",
                    style = RcType.Label,
                    color = RcColors.TextSecondary
                )
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    Log.d(tag, "HomeScreen settings button clicked")
                    onSettings()
                },
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp),
                border = ButtonDefaults.outlinedButtonBorder(
                    borderColor = RcColors.Divider
                )
            ) {
                Text(
                    text = "⚙ 코치 설정",
                    style = RcType.Label,
                    color = RcColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    indicatorColor: Color,
    trackColor: Color
) {
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(indicatorColor)
        )
    }
}
