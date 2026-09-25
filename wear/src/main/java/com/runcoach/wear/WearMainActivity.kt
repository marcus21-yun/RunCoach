package com.runcoach.wear

import android.util.Log
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.runcoach.wear.ui.briefing.PostBriefingScreen
import com.runcoach.wear.ui.briefing.PreBriefingScreen
import com.runcoach.wear.ui.goal.AiGoalScreen
import com.runcoach.wear.ui.history.HistoryScreen
import com.runcoach.wear.ui.home.HomeScreen
import com.runcoach.wear.ui.postrun.PostRunScreen
import com.runcoach.wear.ui.prerun.PreRunScreen
import com.runcoach.wear.ui.running.RunningScreen
import com.runcoach.wear.ui.running.RunningViewModel
import com.runcoach.wear.ui.settings.CoachStyleSettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay

object Routes {
    const val HOME           = "home"
    const val PRE_RUN        = "pre_run"
    const val PRE_BRIEFING   = "pre_briefing"
    const val RUNNING        = "running"
    const val POST_BRIEFING  = "post_briefing"
    const val POST_RUN       = "post_run"
    const val AI_GOAL        = "ai_goal"
    const val HISTORY        = "history"
    const val COACH_SETTINGS = "coach_settings"
}

@AndroidEntryPoint
class WearMainActivity : ComponentActivity() {
    private val runningViewModel: RunningViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberSwipeDismissableNavController()
            LaunchedEffect(navController) {
                navController.currentBackStackEntryFlow.collectLatest { entry ->
                    Log.d("RunCoachWear", "route=${entry.destination.route}")
                }
            }

            SwipeDismissableNavHost(
                navController = navController,
                startDestination = Routes.HOME
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onStartRun = { navController.navigate(Routes.PRE_RUN) },
                        onHistory  = { navController.navigate(Routes.HISTORY) },
                        onSettings = { navController.navigate(Routes.COACH_SETTINGS) }
                    )
                }

                composable(Routes.COACH_SETTINGS) {
                    CoachStyleSettingsScreen(navController = navController)
                }

                composable(Routes.PRE_RUN) {
                    PreRunScreen(
                        onStart = { navController.navigate(Routes.PRE_BRIEFING) },
                        onBack  = { navController.popBackStack() }
                    )
                }

                // 사전 브리핑 — WearDataStore에서 자동으로 데이터 로드
                composable(Routes.PRE_BRIEFING) {
                    PreBriefingScreen(navController = navController)
                }

                composable(Routes.RUNNING) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    RunningScreen(
                        viewModel = runningViewModel,
                        onFinish  = {
                            runningViewModel.finishRun(context)
                            navController.navigate(Routes.POST_BRIEFING) {
                                popUpTo(Routes.HOME)
                            }
                        }
                    )
                }

                // 사후 브리핑 — RunningViewModel의 RunResult 사용
                composable(Routes.POST_BRIEFING) {
                    val result by runningViewModel.runResult.collectAsState()
                    var timedOut by remember { mutableStateOf(false) }

                    LaunchedEffect(result) {
                        if (result == null) {
                            timedOut = false
                            delay(1500)
                            if (runningViewModel.runResult.value == null) {
                                timedOut = true
                            }
                        } else {
                            timedOut = false
                        }
                    }

                    if (result != null) {
                        PostBriefingScreen(
                            navController = navController,
                            runResult = result!!
                        )
                    } else if (timedOut) {
                        navController.navigate(Routes.POST_RUN) {
                            popUpTo(Routes.POST_BRIEFING) { inclusive = true }
                        }
                    } else {
                        Box(
                            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                composable(Routes.POST_RUN) {
                    PostRunScreen(
                        onAiGoal = { navController.navigate(Routes.AI_GOAL) },
                        onHome   = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Routes.AI_GOAL) {
                    AiGoalScreen(
                        onAccept = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Routes.HISTORY) {
                    HistoryScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
