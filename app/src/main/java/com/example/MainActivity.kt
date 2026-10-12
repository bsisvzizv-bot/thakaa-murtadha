package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MainQaScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.QaViewModel
import com.example.ui.viewmodel.VideoViewModel

enum class AppScreen {
    MAIN,
    HISTORY,
    SETTINGS,
    VIDEO_STUDIO
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Ensure native Right-to-Left support for Arabic
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    qaViewModel: QaViewModel = viewModel(),
    videoViewModel: VideoViewModel = viewModel()
) {
    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }
    val customApiKey by qaViewModel.customApiKey.collectAsState()

    when (currentScreen) {
        AppScreen.MAIN -> {
            MainQaScreen(
                viewModel = qaViewModel,
                onNavigateToHistory = { currentScreen = AppScreen.HISTORY },
                onNavigateToSettings = { currentScreen = AppScreen.SETTINGS },
                onNavigateToVideoStudio = { currentScreen = AppScreen.VIDEO_STUDIO }
            )
        }
        AppScreen.HISTORY -> {
            HistoryScreen(
                viewModel = qaViewModel,
                onBack = { currentScreen = AppScreen.MAIN }
            )
        }
        AppScreen.SETTINGS -> {
            SettingsScreen(
                viewModel = qaViewModel,
                onBack = { currentScreen = AppScreen.MAIN }
            )
        }
        AppScreen.VIDEO_STUDIO -> {
            VideoStudioScreen(
                viewModel = videoViewModel,
                customApiKey = customApiKey.ifBlank { null },
                onBack = { currentScreen = AppScreen.MAIN }
            )
        }
    }
}
