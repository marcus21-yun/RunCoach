package com.runcoach.wear.ui.briefing

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.material.*
import com.runcoach.wear.sensor.RunResult
import com.runcoach.wear.ui.theme.RcColors
import com.runcoach.wear.ui.theme.RcType

// ── 사전 브리핑 화면 ────────────────────────────────────────────

@Composable
fun PreBriefingScreen(
    navController: NavController,
    viewModel: BriefingViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadAndSpeakPreBriefing()
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is BriefingUiState.Done) {
            delay(400) // TTS 마지막 문장 잘림 방지
            navController.navigate("running") {
                popUpTo("pre_briefing") { inclusive = true }
            }
        }
    }

    BriefingContent(
        tag = "🎯 사전 브리핑",
        subLabel = "천천히 걸으며 준비하세요",
        tagColor = Color(0xFF00E676),
        uiState = uiState,
        onSkip = {
            viewModel.skip()
            navController.navigate("running") {
                popUpTo("pre_briefing") { inclusive = true }
            }
        }
    )
}

// ── 사후 브리핑 화면 ────────────────────────────────────────────

@Composable
fun PostBriefingScreen(
    navController: NavController,
    runResult: RunResult,
    viewModel: BriefingViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.loadAndSpeakPostBriefing(runResult)
    }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is BriefingUiState.Done) {
            navController.navigate("post_run") {
                popUpTo("post_briefing") { inclusive = true }
            }
        }
    }

    BriefingContent(
        tag = "🏁 운동 후 피드백",
        subLabel = "오늘 러닝 분석 중...",
        tagColor = Color(0xFFAB47BC),
        uiState = uiState,
        onSkip = {
            viewModel.skip()
            navController.navigate("post_run") {
                popUpTo("post_briefing") { inclusive = true }
            }
        }
    )
}

// ── 공통 브리핑 콘텐츠 ─────────────────────────────────────────

@Composable
private fun BriefingContent(
    tag: String,
    subLabel: String,
    tagColor: Color,
    uiState: BriefingUiState,
    onSkip: () -> Unit
) {
    val scrollState = rememberScrollState()
    // Ready 시점의 브리핑 텍스트를 Speaking 동안에도 화면에 유지(무음 환경 대응)
    var cachedText by remember { mutableStateOf("") }
    if (uiState is BriefingUiState.Ready) cachedText = uiState.text

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RcColors.Background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                // 태그
                Text(
                    text = tag,
                    color = tagColor,
                    style = RcType.Label,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                when (uiState) {
                    is BriefingUiState.Loading -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            indicatorColor = tagColor
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = subLabel,
                            color = RcColors.TextSecondary,
                            style = RcType.Label
                        )
                    }

                    is BriefingUiState.Ready -> {
                        BriefingBody(
                            text = uiState.text,
                            tagColor = tagColor,
                            isSpeaking = false
                        )
                    }

                    is BriefingUiState.Speaking -> {
                        // 캐싱한 텍스트를 음성과 함께 화면에도 표시
                        BriefingBody(
                            text = cachedText,
                            tagColor = tagColor,
                            isSpeaking = true
                        )
                    }

                    is BriefingUiState.Error -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "브리핑을 불러오지 못했습니다",
                            color = RcColors.TextSecondary,
                            style = RcType.Label,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> {}
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 건너뛰기 버튼
                Button(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = RcColors.Surface
                    )
                ) {
                    Text(
                        text = "건너뛰기 ▶",
                        color = RcColors.TextSecondary,
                        style = RcType.Label
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun BriefingBody(text: String, tagColor: Color, isSpeaking: Boolean) {
    if (text.isNotEmpty()) {
        Text(
            text = "\"$text\"",
            color = RcColors.TextBody,
            style = RcType.Body,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
    }
    AudioWaveAnimation(isActive = isSpeaking, color = tagColor)
}

@Composable
private fun AudioWaveAnimation(isActive: Boolean, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
    ) {
        repeat(5) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "wave_$index")
            val scaleY by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        delayMillis = index * 80,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .graphicsLayer(scaleY = if (isActive) scaleY else 0.3f)
                    .background(
                        color = if (isActive) color else color.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
