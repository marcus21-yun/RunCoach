package com.runcoach.wear.ui.running

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.OutlinedButton
import androidx.wear.compose.material.Text
import com.runcoach.core.HrZone
import com.runcoach.core.getHrZone
import com.runcoach.wear.alarm.AlarmType
import com.runcoach.wear.ui.theme.RcColors
import com.runcoach.wear.ui.theme.RcType

@Composable
fun RunningScreen(
    onFinish: () -> Unit,
    viewModel: RunningViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current

    // 달리기 중 화면 꺼짐 방지
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val state by viewModel.uiState.collectAsState()
    val activeAlarm by viewModel.activeAlarm.collectAsState()
    val coachMessage by viewModel.coachMessage.collectAsState()
    var runStarted by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val requiredPermissions = requiredRunPermissions()
        val allGranted = requiredPermissions.all { permission ->
            grants[permission] == true || hasPermission(context, permission)
        }
        if (allGranted) {
            permissionMessage = null
            if (!runStarted) {
                runStarted = true
                viewModel.startRun(context)
            }
        } else {
            permissionMessage = "권한이 없어 러닝을 시작할 수 없습니다."
        }
    }

    LaunchedEffect(Unit) {
        if (hasAllRunPermissions(context)) {
            runStarted = true
            viewModel.startRun(context)
        } else {
            permissionLauncher.launch(requiredRunPermissions())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RcColors.Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // GPS 상태 — 신호 확보 전까지 안내(거리가 0인 이유를 사용자에게 표시)
            if (!state.gpsReady) {
                Text(
                    text = "🛰 GPS 신호 찾는 중…",
                    style = RcType.Label,
                    color = RcColors.Warning,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            // 히어로 지표: 거리
            Text(
                text = "%.2f".format(state.distanceKm),
                style = RcType.Display,
                color = RcColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(text = "킬로미터", style = RcType.Label, color = RcColors.TextSecondary)

            MetricDivider()

            // 보조 핵심: 페이스 · 심박 (24sp Numeral + 라벨)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.currentPace,
                        style = RcType.MetricValue,
                        color = RcColors.TextPrimary
                    )
                    Text(text = "페이스/km", style = RcType.Label, color = RcColors.TextSecondary)
                }
                Text(text = "|", color = RcColors.Divider, fontSize = 24.sp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.heartRate}",
                        style = RcType.MetricValue,
                        color = heartRateColor(state.heartRate)
                    )
                    Text(text = "심박 bpm", style = RcType.Label, color = RcColors.TextSecondary)
                }
            }

            MetricDivider()

            val diffText = if (state.diffFromLastKm >= 0) {
                "+%.1fkm".format(state.diffFromLastKm)
            } else {
                "%.1fkm".format(state.diffFromLastKm)
            }
            val ahead = state.diffFromLastKm >= 0
            val diffColor = if (ahead) RcColors.Success else RcColors.Danger

            // 코치가 방금 한 말이 있으면 기록 비교 대신 보여준다 (한 화면, 한 행동)
            val coachLine = coachMessage
            if (coachLine != null) {
                Text(
                    text = "🗣 $coachLine",
                    style = RcType.Caption,
                    color = RcColors.Accent,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "${if (ahead) "▲" else "▼"} 지난 기록 대비 $diffText",
                    style = RcType.Label,
                    color = diffColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(text = formatTime(state.elapsedSec), style = RcType.Label, color = RcColors.TextSecondary)
                Text(text = "피로도 ${state.fatigueLabel}", style = RcType.Label, color = fatigueColor(state.fatigueLevel))
            }

            Spacer(modifier = Modifier.height(12.dp))

            permissionMessage?.let { message ->
                Text(
                    text = message,
                    style = RcType.Caption,
                    color = RcColors.Warning,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { permissionLauncher.launch(requiredRunPermissions()) },
                    modifier = Modifier
                        .width(160.dp)
                        .height(48.dp)
                ) {
                    Text(text = "권한 다시 요청", style = RcType.Label, color = RcColors.TextBody)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier
                    .width(120.dp)
                    .height(48.dp)
            ) {
                Text(text = "종료", style = RcType.Label, color = RcColors.TextBody)
            }
        }

        AnimatedVisibility(
            visible = activeAlarm != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            activeAlarm?.let { alarm ->
                AlarmOverlay(alarm = alarm)
            }
        }
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(RcColors.Divider)
    )
}

@Composable
fun AlarmOverlay(alarm: AlarmEvent) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(alarm.type.overlayColor.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = alarm.type.emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alarm.title,
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = alarm.message,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}

val AlarmType.overlayColor get() = when (this) {
    AlarmType.HEART_RATE -> Color(0xFFB71C1C)
    AlarmType.SPEEDUP -> Color(0xFF0D47A1)
    AlarmType.DISTANCE -> Color(0xFF1B5E20)
    AlarmType.PACE_DROP -> Color(0xFFF57F17)
}

val AlarmType.emoji get() = when (this) {
    AlarmType.HEART_RATE -> "심박"
    AlarmType.SPEEDUP -> "속도"
    AlarmType.DISTANCE -> "거리"
    AlarmType.PACE_DROP -> "페이스"
}

fun heartRateColor(bpm: Int) = when (getHrZone(bpm)) {
    HrZone.DANGER  -> RcColors.Danger
    HrZone.CAUTION -> RcColors.Warning
    HrZone.SAFE    -> RcColors.Success
}

fun fatigueColor(level: String) = when (level) {
    "high" -> RcColors.Danger
    "mid" -> RcColors.Warning
    else -> RcColors.Success
}

fun formatTime(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}

private fun requiredRunPermissions(): Array<String> {
    return arrayOf(
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACCESS_FINE_LOCATION,
        "android.permission.health.READ_HEART_RATE"
    )
}

private fun hasAllRunPermissions(context: Context): Boolean =
    requiredRunPermissions().all { hasPermission(context, it) }

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
