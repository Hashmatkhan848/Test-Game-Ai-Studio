package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.game.GameViewModel
import com.example.game.ScreenState
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090614)
                ) {
                    val currentProgress by viewModel.playerProgress.collectAsState()
                    val voyageHistoryList by viewModel.voyageHistory.collectAsState()

                    if (currentProgress == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .galacticSpaceBackground(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF00FFCC))
                        }
                    } else {
                        AnimatedContent(
                            targetState = viewModel.currentScreen,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                            label = "GalacticRiftScreenAnimator"
                        ) { screen ->
                            when (screen) {
                                ScreenState.HANGAR -> {
                                    HangarScreen(
                                        viewModel = viewModel,
                                        progress = currentProgress!!,
                                        onStartVoyage = { viewModel.startNewVoyage() },
                                        onNavigateToHistory = { viewModel.navigateToHistory() }
                                    )
                                }
                                ScreenState.COMBAT -> {
                                    SpaceCombatScreen(
                                        viewModel = viewModel,
                                        progress = currentProgress!!,
                                        onGameTerminated = { viewModel.navigateToHangar() }
                                    )
                                }
                                ScreenState.SUMMARY -> {
                                    GameSummaryScreen(
                                        viewModel = viewModel,
                                        onReturnToHangar = { viewModel.navigateToHangar() },
                                        onLaunchAgain = { viewModel.startNewVoyage() }
                                    )
                                }
                                ScreenState.HISTORY -> {
                                    HistoryScreen(
                                        voyages = voyageHistoryList,
                                        onBack = { viewModel.navigateToHangar() },
                                        onClearHistory = { viewModel.clearHistoricalRuns() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

