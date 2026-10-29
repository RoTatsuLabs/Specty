package com.rotatsulabs.specty.ui.design

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
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

    /** Vertical distance the finger has moved since the swipe began, in pixels. */
    var dy by mutableFloatStateOf(0f)
        private set

    private var startY = 0f

    internal fun update(event: BackEventCompat) {
        if (!active) startY = event.touchY
        dy = event.touchY - startY
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

enum class OverlayStyle {
    /** Opaque surface of its own ([backdrop]); the screen below shows around it while it is pulled back. */
    Side,

    /** The content brings its own surface. */
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
    val lift = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(visible) }
    var direction by remember { mutableFloatStateOf(1f) }
    val isVisible by rememberUpdatedState(visible)

    LaunchedEffect(visible) {
        if (visible) {
            direction = 1f
            composed = true
            lift.snapTo(0f)
            pose.animateTo(0f, Motion.spatial())
        } else {
            pose.animateTo(1f, exit)
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
                    lift.snapTo(gesture.dy)
                }
            } else if (following) {
                following = false
                // A cancelled swipe springs back. A committed one is closed by `visible` turning false.
                if (!gesture.committed && isVisible) {
                    scope.launch { pose.animateTo(0f, Motion.spatial()) }
                    scope.launch { lift.animateTo(0f, Motion.spatial()) }
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
    // The whole screen follows the swipe like the system's own back preview: it shrinks, rounds its corners and
    // slides away from the edge, with the screen below showing around it.
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = pose.value
                val s = BackMath.scale(p)
                scaleX = s
                scaleY = s
                translationX = direction * size.width * BackMath.shift(p)
                translationY = BackMath.lift(lift.value, size.height * BackMath.LIFT_LIMIT)
                transformOrigin = TransformOrigin.Center
                alpha = BackMath.alpha(p)
                val corner = BackMath.cornerShare(p)
                if (corner > 0f) {
                    shape = RoundedCornerShape((32.dp * corner).toPx())
                    clip = true
                }
            }
            .then(if (style == OverlayStyle.Side && backdrop != Color.Transparent) Modifier.background(backdrop) else Modifier),
    ) { content() }
}
