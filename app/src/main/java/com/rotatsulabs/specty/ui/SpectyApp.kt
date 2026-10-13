package com.rotatsulabs.specty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rotatsulabs.specty.ui.screens.AnalysisScreen
import com.rotatsulabs.specty.ui.screens.HomeScreen
import com.rotatsulabs.specty.ui.screens.LibraryScreen
import com.rotatsulabs.specty.ui.screens.SettingsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Library("Library", Icons.Rounded.LibraryMusic),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
fun SpectyApp(viewModel: MainViewModel, onPickFile: () -> Unit) {
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val fftSize by viewModel.fftSize.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val analysisOpen = analysis !is AnalysisState.Idle

    BackHandler(enabled = analysisOpen) { viewModel.closeAnalysis() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = {
                            if (analysisOpen) viewModel.closeAnalysis()
                            tabIndex = index
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            if (analysisOpen) {
                AnalysisScreen(analysis, onBack = viewModel::closeAnalysis)
            } else {
                when (Tab.entries[tabIndex]) {
                    Tab.Home -> HomeScreen(
                        onPickFile = onPickFile,
                        onOpenLibrary = { tabIndex = Tab.Library.ordinal },
                    )
                    Tab.Library -> LibraryScreen(
                        state = library,
                        onLoad = viewModel::loadLibrary,
                        onOpen = { viewModel.analyze(it.uri) },
                    )
                    Tab.Settings -> SettingsScreen(fftSize = fftSize, onFftSize = viewModel::setFftSize)
                }
            }
        }
    }
}
