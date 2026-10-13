package com.rotatsulabs.specty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rotatsulabs.specty.ui.screens.AnalysisScreen
import com.rotatsulabs.specty.ui.screens.LibraryScreen
import com.rotatsulabs.specty.ui.screens.SettingsSheet
import com.rotatsulabs.specty.ui.theme.Motion

@Composable
fun SpectyApp(viewModel: MainViewModel, onPickFile: () -> Unit) {
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val fftSize by viewModel.fftSize.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val analysisOpen = analysis !is AnalysisState.Idle

    // Keep the last non-idle state around so the screen can animate out with its content intact.
    val held = remember { object { var state: AnalysisState = AnalysisState.Idle } }
    if (analysisOpen) held.state = analysis

    BackHandler(enabled = analysisOpen) { viewModel.closeAnalysis() }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            LibraryScreen(
                state = library,
                onLoad = viewModel::loadLibrary,
                onOpen = { viewModel.analyze(it.uri) },
                onPickFile = onPickFile,
                onOpenSettings = { showSettings = true },
            )
        }

        AnimatedVisibility(
            visible = analysisOpen,
            enter = slideInVertically(Motion.spatial()) { it / 5 } + scaleIn(Motion.spatial(), initialScale = 0.9f) + fadeIn(Motion.effects()),
            exit = slideOutVertically(Motion.spatial()) { it / 8 } + scaleOut(Motion.spatial(), targetScale = 0.94f) + fadeOut(Motion.effects()),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.navigationBarsPadding()) {
                    AnalysisScreen(held.state, onBack = viewModel::closeAnalysis)
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(fftSize = fftSize, onFftSize = viewModel::setFftSize, onDismiss = { showSettings = false })
    }
}
