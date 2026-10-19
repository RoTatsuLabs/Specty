package com.rotatsulabs.specty.ui.design

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.theme.Motion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** State of one back swipe, shared between the handler that receives it and the screen that follows it. */
@Stable
class BackGesture {
    /** 0 at rest to 1 for a full swipe. Only meaningful while [active]. */
    var progress by mutableFloatStateOf(0f)
        private set

    var active by mutableStateOf(false)
        private set

    /** True when the swipe began at the right edge, so a sliding screen leaves to the left. */
    var fromRight by mutableStateOf(false)
        private set

    /** True once the swipe was released as a back, false when it was cancelled. */
    var committed by mutableStateOf(false)
        private set

    internal fun update(event: BackEventCompat) {
        committed = false
        active = true
        progress = event.progress
        fromRight = event.swipeEdge == BackEventCompat.EDGE_RIGHT
    }

    internal fun finish(commit: Boolean) {
        committed = commit
        active = false
    }
}

/**
 * Back handling that reports the swipe to [gesture] as it happens, then calls [onBack] on release. Without a
 * gesture (button navigation, or before Android 14) there are no events and [onBack] simply runs.
 */
@Composable
fun PredictiveBack(gesture: BackGesture, enabled: Boolean = true, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { gesture.update(it) }
            gesture.finish(commit = true)
            current()
        } catch (e: CancellationException) {
            gesture.finish(commit = false)
            throw e
        }
    }
}

/** Which of the screens that cover the library are open, so the library can ease back while one is. */
@Stable
class OverlayDepth {
    private val levels = mutableStateMapOf<String, Float>()

    /** 0 with nothing open, up to 1 with a screen fully open. */
    val level: Float get() = levels.values.maxOrNull() ?: 0f

    internal fun report(id: String, shown: Float) {
        levels[id] = shown
    }

    internal fun clear(id: String) {
        levels.remove(id)
    }
}

/** The screen underneath settles back a little as another opens over it, and returns as it leaves. */
fun Modifier.underlay(depth: OverlayDepth): Modifier = graphicsLayer {
    val s = BackMath.underlayScale(depth.level)
    scaleX = s
    scaleY = s
}

/**
 * How far the screen the reader is looking at has left, shared down to its components. Each component reads it
 * through [followBack] and moves on its own, instead of the whole screen moving as one sheet.
 */
@Stable
class BackPose(val leave: () -> Float, val direction: () -> Float = { 1f })

val LocalBackPose = compositionLocalOf<BackPose?> { null }

/** Applies the part of a screen's exit that belongs to the component at [index]. Call at the end of a graphicsLayer. */
fun GraphicsLayerScope.followBack(back: BackPose?, index: Int) {
    if (back == null) return
    val part = BackMath.part(back.leave(), index)
    if (part <= 0f) return
    alpha *= BackMath.partAlpha(part)
    val k = BackMath.partScale(part)
    scaleX *= k
    scaleY *= k
    translationX += back.direction() * size.width * BackMath.partDrift(part)
}

enum class OverlayStyle {
    /** Fades in over the screen below; its components arrive and leave one by one along the swipe. */
    Side,

    /** Grows in from slightly below and settles back down. */
    Rise,
}

/**
 * A full screen that opens over the one below it and follows the back swipe. One [Animatable] pose drives
 * everything: opening and closing are springs, and the swipe writes to the same value, so releasing continues from
 * the exact place the finger left instead of jumping to a canned exit.
 */
@Composable
fun OverlayScreen(
    visible: Boolean,
    gesture: BackGesture,
    depth: OverlayDepth,
    id: String,
    modifier: Modifier = Modifier,
    style: OverlayStyle = OverlayStyle.Side,
    exit: AnimationSpec<Float> = Motion.spatial(),
    backdrop: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val pose = remember { Animatable(if (visible) 0f else 1f) }
    val leave = remember { Animatable(if (visible) 0f else 1f) }
    var composed by remember { mutableStateOf(visible) }
    var direction by remember { mutableFloatStateOf(1f) }
    val isVisible by rememberUpdatedState(visible)
    val backPose = remember { BackPose({ leave.value }, { direction }) }

    LaunchedEffect(visible) {
        if (visible) {
            direction = 1f
            composed = true
            leave.snapTo(0f)
            pose.animateTo(0f, Motion.spatial())
        } else {
            coroutineScope {
                launch { leave.animateTo(1f, exit) }
                pose.animateTo(1f, exit)
            }
            composed = false
        }
    }

    LaunchedEffect(gesture) {
        val scope = this
        var following = false
        snapshotFlow { Triple(gesture.active, gesture.progress, gesture.fromRight) }.collect { (active, progress, fromRight) ->
            if (active) {
                following = true
                if (isVisible) {
                    direction = if (fromRight) -1f else 1f
                    val at = BackMath.pose(progress)
                    pose.snapTo(at)
                    leave.snapTo(at)
                }
            } else if (following) {
                following = false
                // A cancelled swipe springs back. A committed one is closed by `visible` turning false.
                if (!gesture.committed && isVisible) {
                    scope.launch { pose.animateTo(0f, Motion.spatial()) }
                    scope.launch { leave.animateTo(0f, Motion.spatial()) }
                }
            }
        }
    }

    DisposableEffect(id) {
        onDispose { depth.clear(id) }
    }
    LaunchedEffect(depth, id) {
        snapshotFlow { 1f - pose.value.coerceIn(0f, 1f) }.collect { depth.report(id, it) }
    }

    if (!composed) return
    when (style) {
        // The surface stays put and opaque; the components on it leave one by one.
        OverlayStyle.Side -> Box(modifier.fillMaxSize()) {
            if (backdrop != Color.Transparent) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = BackMath.alpha(pose.value) }.background(backdrop))
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = BackMath.alpha(pose.value) }) {
                CompositionLocalProvider(LocalBackPose provides backPose) { content() }
            }
        }
        OverlayStyle.Rise -> Box(
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = pose.value
                    val s = BackMath.riseScale(p)
                    scaleX = s
                    scaleY = s
                    translationY = size.height * BackMath.riseTravel(p)
                    transformOrigin = TransformOrigin.Center
                    alpha = BackMath.alpha(p)
                    val corner = BackMath.cornerShare(p)
                    if (corner > 0f) {
                        shape = RoundedCornerShape((32.dp * corner).toPx())
                        clip = true
                    }
                },
        ) { content() }
    }
}
