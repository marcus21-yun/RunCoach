package com.runcoach.wear.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.material.*
import com.runcoach.wear.data.BriefingRepository
import com.runcoach.wear.data.SupabaseSyncService
import com.runcoach.wear.data.WearDataStore
import com.runcoach.wear.data.api.OpenAiApiService
import com.runcoach.wear.data.model.CoachStyle
import com.runcoach.wear.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────

@HiltViewModel
class CoachStyleViewModel @Inject constructor(
    application: Application,
    private val supabaseSync: SupabaseSyncService
) : AndroidViewModel(application) {

    private val repository = BriefingRepository(
        context = application,
        apiService = OpenAiApiService(apiKey = BuildConfig.OPENAI_API_KEY)
    )

    private val _selectedStyle = MutableStateFlow(CoachStyle.CUTE)
    val selectedStyle: StateFlow<CoachStyle> = _selectedStyle

    init {
        viewModelScope.launch {
            // Supabase에서 최신 코치 스타일 가져오기 → 없으면 로컬 DataStore 사용
            val remoteStyle = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                supabaseSync.fetchCoachStyle()
            }
            if (remoteStyle != null) {
                repository.saveCoachStyle(remoteStyle)
                _selectedStyle.value = remoteStyle
            } else {
                _selectedStyle.value = repository.loadCoachStyle()
            }
        }
    }

    fun selectStyle(style: CoachStyle) {
        viewModelScope.launch {
            repository.saveCoachStyle(style)
            _selectedStyle.value = style
            // 백그라운드에서 Supabase에 동기화
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                supabaseSync.saveCoachStyle(style)
            }
        }
    }
}

// ── Screen ────────────────────────────────────────────────────

@Composable
fun CoachStyleSettingsScreen(
    navController: NavController,
    viewModel: CoachStyleViewModel = hiltViewModel()
) {
    val selected by viewModel.selectedStyle.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0E1A)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    text = "코치 스타일",
                    color = Color(0xFF00E676),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            items(CoachStyle.entries.size) { index ->
                val style = CoachStyle.entries[index]
                StyleItem(
                    style = style,
                    isSelected = style == selected,
                    onClick = { viewModel.selectStyle(style) }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun StyleItem(
    style: CoachStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFF00E676) else Color(0xFF1F2937)
    val bgColor     = if (isSelected) Color(0xFF0D2018) else Color(0xFF161D2E)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = style.emoji, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = style.displayName,
                color = if (isSelected) Color(0xFF00E676) else Color(0xFFF0F4FF),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (isSelected) {
            Text(text = "✓", color = Color(0xFF00E676), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
