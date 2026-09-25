package com.runcoach.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.content.ContextCompat
import com.runcoach.app.ui.dashboard.DashboardScreen
import com.runcoach.app.ui.detail.RunDetailScreen
import com.runcoach.app.ui.history.HistoryScreen
import com.runcoach.app.ui.prediction.PredictionScreen
import com.runcoach.app.ui.stats.StatsScreen
import dagger.hilt.android.AndroidEntryPoint

object PhoneRoutes {
    const val DASHBOARD  = "dashboard"
    const val DETAIL     = "detail/{recordId}"
    const val HISTORY    = "history?shareCode={shareCode}"
    const val STATS      = "stats"
    const val PREDICTION = "prediction"

    fun detail(id: Long) = "detail/$id"
    fun history(shareCode: String? = null) = if (shareCode.isNullOrBlank()) "history" else "history?shareCode=$shareCode"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val deepLinkShareCode = mutableStateOf<String?>(null)
    private val openHistoryImport = mutableStateOf(false)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        deepLinkShareCode.value = extractShareCode(intent)
        openHistoryImport.value = intent?.getBooleanExtra("open_history_import", false) == true
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF0A0A0A),
                    surface    = Color(0xFF1A1A1A),
                    primary    = Color(0xFF4FC3F7)
                )
            ) {
                val navController = rememberNavController()
                val initialShareCode by deepLinkShareCode
                val shouldOpenHistoryImport by openHistoryImport

                LaunchedEffect(shouldOpenHistoryImport) {
                    if (shouldOpenHistoryImport && navController.currentDestination?.route != PhoneRoutes.HISTORY) {
                        navController.navigate(PhoneRoutes.history())
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = if (shouldOpenHistoryImport) {
                        PhoneRoutes.history()
                    } else if (initialShareCode.isNullOrBlank()) {
                        PhoneRoutes.DASHBOARD
                    } else {
                        PhoneRoutes.history(initialShareCode)
                    },
                    modifier = Modifier.background(Color(0xFF0A0A0A))
                ) {
                    composable(PhoneRoutes.DASHBOARD) {
                        DashboardScreen(
                            onRecordClick  = { id -> navController.navigate(PhoneRoutes.detail(id)) },
                            onHistory      = { navController.navigate(PhoneRoutes.history()) },
                            onStats        = { navController.navigate(PhoneRoutes.STATS) },
                            onPrediction   = { navController.navigate(PhoneRoutes.PREDICTION) }
                        )
                    }
                    composable(
                        route = PhoneRoutes.HISTORY,
                        arguments = listOf(
                            navArgument("shareCode") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        HistoryScreen(
                            navController = navController,
                            initialShareCode = backStackEntry.arguments?.getString("shareCode"),
                            autoImportOnStart = shouldOpenHistoryImport,
                            onAutoImportHandled = { openHistoryImport.value = false }
                        )
                    }
                    composable(
                        route = PhoneRoutes.DETAIL,
                        arguments = listOf(navArgument("recordId") { type = NavType.LongType })
                    ) { back ->
                        val id = back.arguments?.getLong("recordId") ?: return@composable
                        RunDetailScreen(
                            recordId = id,
                            onBack   = { navController.popBackStack() }
                        )
                    }
                    composable(PhoneRoutes.STATS) {
                        StatsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(PhoneRoutes.PREDICTION) {
                        PredictionScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestNotificationPermissionIfNeeded()
        deepLinkShareCode.value = extractShareCode(intent)
        openHistoryImport.value = intent.getBooleanExtra("open_history_import", false)
    }

    private fun extractShareCode(intent: Intent?): String? =
        intent?.data?.getQueryParameter("code")

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
