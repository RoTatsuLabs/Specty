package com.rotatsulabs.specty.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.components.AnalysisDock
import com.rotatsulabs.specty.ui.components.withoutExplicitMark
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
import com.rotatsulabs.specty.ui.screens.AboutScreen
import com.rotatsulabs.specty.ui.screens.AnalysisScreen
import com.rotatsulabs.specty.ui.screens.LibraryScreen
import com.rotatsulabs.specty.ui.screens.SettingsScreen
import com.rotatsulabs.specty.ui.design.Axis
import com.rotatsulabs.specty.ui.design.ContainerTransform
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.CoverFlight
import com.rotatsulabs.specty.ui.design.CoverFlightLayer
import com.rotatsulabs.specty.ui.design.BackGesture
import com.rotatsulabs.specty.ui.design.OverlayDepth
import com.rotatsulabs.specty.ui.design.OverlayScreen
import com.rotatsulabs.specty.ui.design.OverlayStyle
import com.rotatsulabs.specty.ui.design.PredictiveBack
import com.rotatsulabs.specty.ui.design.underlay
import com.rotatsulabs.specty.ui.design.FadeThrough
import com.rotatsulabs.specty.ui.design.SharedAxis
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.rememberContainerOrigin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.rotatsulabs.specty.ui.theme.Motion
import com.rotatsulabs.specty.ui.theme.MotionTokens

@Composable
fun SpectyApp(viewModel: MainViewModel, onPickFile: () -> Unit, onPickFolder: () -> Unit, onPickCompare: () -> Unit) {
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val fftSize by viewModel.fftSize.collectAsStateWithLifecycle()
    val windowFn by viewModel.windowFn.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val verdicts by viewModel.cachedVerdicts.collectAsStateWithLifecycle()
    val batch by viewModel.batch.collectAsStateWithLifecycle()
    val developer by viewModel.developer.collectAsStateWithLifecycle()
    val logging by viewModel.logging.collectAsStateWithLifecycle()
    val compare by viewModel.compare.collectAsStateWithLifecycle()
    val onboarding by viewModel.onboarding.collectAsStateWithLifecycle()
    val compareHeld = remember { object { var state: CompareState = CompareState.Idle } }
    if (compare !is CompareState.Idle) compareHeld.state = compare
    val origin = rememberContainerOrigin()
    val flight = remember { CoverFlight() }
    var dockBounds by remember { mutableStateOf<Rect?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showLog by rememberSaveable { mutableStateOf(false) }
    var viewerView by rememberSaveable { mutableStateOf<Int?>(null) }
    // Hashing every track is not free, so remember each key for as long as the settings it depends on stay put.
    val keyFor = remember(fftSize, windowFn) {
        val memo = HashMap<com.rotatsulabs.specty.data.Track, String>()
        val lookup: (com.rotatsulabs.specty.data.Track) -> String = { track ->
            memo.getOrPut(track) { viewModel.cacheKey(track.uri, track.sizeBytes, track.modifiedSec, fftSize, windowFn) }
        }
        lookup
    }
    val analysisOpen by viewModel.viewing.collectAsStateWithLifecycle()
    val dockVisible = !analysisOpen && analysis !is AnalysisState.Idle

    // Keep the last non-idle state around so the screen can animate out with its content intact.
    val held = remember { object { var state: AnalysisState = AnalysisState.Idle } }
    if (analysisOpen) held.state = analysis
    val dockHeld = remember { object { var state: AnalysisState = AnalysisState.Idle } }
    if (dockVisible) dockHeld.state = analysis

    // Drop a stale viewer request if the analysis it belonged to is gone.
    val viewerResult = (held.state as? AnalysisState.Done)?.result
    if (viewerView != null && (viewerResult == null || !analysisOpen)) viewerView = null
    val viewerHeld = remember { object { var view: Int = 0 } }
    viewerView?.let { viewerHeld.view = it }

    // Each screen that covers another follows its own back swipe. Registered bottom to top: the last one enabled wins.
    val depth = remember { OverlayDepth() }
    val analysisBack = remember { BackGesture() }
    val settingsBack = remember { BackGesture() }
    val aboutBack = remember { BackGesture() }
    val compareBack = remember { BackGesture() }
    val viewerBack = remember { BackGesture() }
    val logBack = remember { BackGesture() }
    PredictiveBack(analysisBack, enabled = analysisOpen) { viewModel.closeAnalysis() }
    PredictiveBack(settingsBack, enabled = showSettings) { showSettings = false }
    PredictiveBack(aboutBack, enabled = showAbout) { showAbout = false }
    PredictiveBack(logBack, enabled = showLog) { showLog = false }
    PredictiveBack(compareBack, enabled = compare !is CompareState.Idle) { viewModel.closeCompare() }
    PredictiveBack(viewerBack, enabled = viewerView != null) { viewerView = null }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize().underlay(depth), color = MaterialTheme.colorScheme.surface) {
            LibraryScreen(
                state = library,
                onLoad = viewModel::loadLibrary,
                onOpen = { track, bounds, cover ->
                    origin.bounds = bounds
                    origin.corners = SpectyShapes.Row
                    flight.begin(track.uri, cover)
                    viewModel.analyze(track.uri)
                },
                flight = flight,
                onPickFile = onPickFile,
                onPickFolder = onPickFolder,
                onRemoveFolder = viewModel::removeFolder,
                onOpenSettings = { showSettings = true },
                onOpenAbout = { showAbout = true },
                dockVisible = dockVisible,
                verdicts = verdicts,
                keyFor = keyFor,
                batch = batch,
                onScanAll = viewModel::scanLibrary,
                onCompare = { a, b -> viewModel.compareTracks(a.uri, b.uri) },
                onCancelScan = viewModel::cancelScan,
                onDismissScan = viewModel::dismissScanSummary,
            )
        }

        AnimatedVisibility(
            visible = dockVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(Motion.spatial()) { it } + fadeIn(Motion.effects()) + scaleIn(Motion.spatial(), initialScale = 0.92f),
            exit = slideOutVertically(Motion.spatial()) { it } + fadeOut(Motion.effects()) + scaleOut(Motion.spatial(), targetScale = 0.92f),
        ) {
            AnalysisDock(
                state = dockHeld.state,
                onOpen = { origin.bounds = dockBounds; origin.corners = Corners(28.dp); flight.clear(); viewModel.openResult() },
                onDismiss = viewModel::closeAnalysis,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .onGloballyPositioned { dockBounds = it.boundsInRoot() },
            )
        }

        ContainerTransform(
            visible = analysisOpen,
            origin = origin,
            modifier = Modifier.fillMaxSize(),
            endCorners = Corners(0.dp),
            gesture = analysisBack,
            background = MaterialTheme.colorScheme.surface,
        ) {
            Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                Box(Modifier.navigationBarsPadding()) {
                    AnalysisScreen(held.state, onBack = viewModel::closeAnalysis, onReanalyse = viewModel::reanalyze, onFullscreen = { viewerView = it }, onCompare = onPickCompare, flight = flight)
                }
            }
        }

        CoverFlightLayer(flight, open = analysisOpen, currentUri = viewerResult?.uri)

        OverlayScreen(visible = showSettings, gesture = settingsBack, depth = depth, id = "settings", backdrop = MaterialTheme.colorScheme.surface) {
            Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                SettingsScreen(
                    appearance = appearance,
                    onThemeMode = viewModel::setThemeMode,
                    onDynamicColor = viewModel::setDynamicColor,
                    onSeed = viewModel::setSeed,
                    onPureBlack = viewModel::setPureBlack,
                    fftSize = fftSize,
                    onFftSize = viewModel::setFftSize,
                    windowFn = windowFn,
                    onWindowFn = viewModel::setWindowFn,
                    savedCount = verdicts.size,
                    onClearCache = viewModel::clearCache,
                    developer = developer,
                    logging = logging,
                    onLogging = viewModel::setLogging,
                    onOpenLog = { showLog = true },
                    onDeveloperOff = { viewModel.setDeveloper(false); showLog = false },
                    onBack = { showSettings = false },
                )
            }
        }

        OverlayScreen(visible = showAbout, gesture = aboutBack, depth = depth, id = "about", backdrop = MaterialTheme.colorScheme.surface) {
            Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                AboutScreen(
                    onReplayIntro = { showAbout = false; viewModel.replayOnboarding() },
                    developer = developer,
                    onUnlockDeveloper = { viewModel.setDeveloper(true) },
                    onBack = { showAbout = false },
                )
            }
        }

        OverlayScreen(visible = showLog, gesture = logBack, depth = depth, id = "log", backdrop = MaterialTheme.colorScheme.surface) {
            Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                com.rotatsulabs.specty.ui.screens.DeveloperLogScreen(onBack = { showLog = false })
            }
        }

        OverlayScreen(
            visible = compare !is CompareState.Idle,
            gesture = compareBack,
            depth = depth,
            id = "compare",
            style = OverlayStyle.Rise,
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.navigationBarsPadding()) {
                    com.rotatsulabs.specty.ui.screens.CompareScreen(compareHeld.state, onBack = viewModel::closeCompare)
                }
            }
        }

        // Leaves quickly: the viewer holds the screen in landscape until it is gone.
        OverlayScreen(
            visible = viewerView != null && viewerResult != null,
            gesture = viewerBack,
            depth = depth,
            id = "viewer",
            style = OverlayStyle.Rise,
            exit = tween(220, easing = MotionTokens.EmphasizedAccelerate),
        ) {
            if (viewerResult != null) {
                Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
                    com.rotatsulabs.specty.ui.spectrogram.SpectrogramViewer(
                        spectrogram = viewerResult.spectrogram,
                        title = viewerResult.info.title.withoutExplicitMark(),
                        initialView = viewerHeld.view,
                        onClose = { viewerView = null },
                        cutoffHz = viewerResult.authenticity.cutoffHz,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = onboarding,
            enter = FadeThrough.enter(),
            exit = FadeThrough.exit(),
        ) {
            com.rotatsulabs.specty.ui.screens.OnboardingScreen(
                onFinish = viewModel::finishOnboarding,
                hasFolders = library.folders.isNotEmpty(),
                onPickFolder = onPickFolder,
            )
        }

    }
}
