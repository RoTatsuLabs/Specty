package com.rotatsulabs.specty.ui.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.rotatsulabs.specty.ui.theme.Motion
import com.rotatsulabs.specty.ui.theme.MotionTokens
import kotlinx.coroutines.launch

/** Material "fade through": the old content fades out, then the new content fades in while scaling up slightly. */
object FadeThrough {
    fun enter(): EnterTransition = fadeIn(tween(MotionTokens.FadeIn, MotionTokens.FadeOut, MotionTokens.EmphasizedDecelerate)) +
        scaleIn(tween(MotionTokens.FadeIn + MotionTokens.FadeOut, 0, MotionTokens.EmphasizedDecelerate), initialScale = 0.92f)

    fun exit(): ExitTransition = fadeOut(tween(MotionTokens.FadeOut, 0, MotionTokens.EmphasizedAccelerate))

}

enum class Axis { X, Y, Z }

/**
 * Material "shared axis": the outgoing and incoming content travel along one axis together.
 * [forward] decides the direction (next step vs. going back); on Z the content scales instead of sliding.
 */
object SharedAxis {
    private const val Dist = 30

    fun enter(axis: Axis, forward: Boolean): EnterTransition {
        val fade = fadeIn(tween(MotionTokens.FadeIn, MotionTokens.FadeOut, MotionTokens.EmphasizedDecelerate))
        val spec = tween<IntOffset>(MotionTokens.Medium, easing = MotionTokens.Emphasized)
        return fade + when (axis) {
            Axis.X -> slideInHorizontally(spec) { (if (forward) Dist else -Dist) * 3 }
            Axis.Y -> slideInVertically(spec) { (if (forward) Dist else -Dist) * 3 }
            Axis.Z -> scaleIn(tween(MotionTokens.Medium, easing = MotionTokens.Emphasized), initialScale = if (forward) 0.8f else 1.1f)
        }
    }

    fun exit(axis: Axis, forward: Boolean): ExitTransition {
        val fade = fadeOut(tween(MotionTokens.FadeOut, 0, MotionTokens.EmphasizedAccelerate))
        val spec = tween<IntOffset>(MotionTokens.Medium, easing = MotionTokens.Emphasized)
        return fade + when (axis) {
            Axis.X -> slideOutHorizontally(spec) { (if (forward) -Dist else Dist) * 3 }
            Axis.Y -> slideOutVertically(spec) { (if (forward) -Dist else Dist) * 3 }
            Axis.Z -> scaleOut(tween(MotionTokens.Medium, easing = MotionTokens.Emphasized), targetScale = if (forward) 1.1f else 0.8f)
        }
    }
}

/** Spring-driven screen transitions: the incoming screen overshoots a little and settles, the outgoing one gives way. */
object Springy {
    fun enterX(forward: Boolean): EnterTransition =
        slideInHorizontally(Motion.spatial()) { if (forward) it / 3 else -it / 3 } +
            fadeIn(Motion.effects()) +
            scaleIn(Motion.spatial(), initialScale = 0.9f)

    fun exitX(forward: Boolean): ExitTransition =
        slideOutHorizontally(Motion.spatial()) { if (forward) -it / 4 else it / 3 } +
            fadeOut(Motion.effects()) +
            scaleOut(Motion.spatial(), targetScale = 0.9f)

    fun enterZ(): EnterTransition =
        scaleIn(Motion.spatial(), initialScale = 0.7f) +
            fadeIn(Motion.effects()) +
            slideInVertically(Motion.spatial()) { it / 10 }

    fun exitZ(): ExitTransition =
        scaleOut(Motion.spatial(), targetScale = 0.8f) +
            fadeOut(Motion.effects()) +
            slideOutVertically(Motion.spatial()) { it / 10 }
}

/** Remembers where a tapped element sits so a destination can grow out of it and shrink back into it. */
@Stable
class ContainerOrigin {
    var bounds: Rect? by mutableStateOf(null)
    var corners: Corners by mutableStateOf(Corners(24.dp))
}

@Composable
fun rememberContainerOrigin() = remember { ContainerOrigin() }

/**
 * Container transform. While [visible], the content grows from [origin]'s bounds to the full available area with a
 * spring, morphing the corner radius from the origin's to [endCorners]. The content itself fades in after the
 * container has started moving, so the two never fight for attention. When [visible] turns false it reverses and
 * [onGone] is called once it has shrunk back.
 */
@Composable
fun ContainerTransform(
    visible: Boolean,
    origin: ContainerOrigin,
    modifier: Modifier = Modifier,
    endCorners: Corners = Corners(0.dp),
    onGone: () -> Unit = {},
    gesture: BackGesture? = null,
    background: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var gone by remember { mutableStateOf(true) }
    val isVisible by rememberUpdatedState(visible)
    if (gesture != null) {
        // The back swipe shrinks the container toward its origin. Releasing continues from there: a cancelled swipe
        // springs open again, a committed one is closed by [visible] turning false.
        LaunchedEffect(gesture) {
            val scope = this
            var following = false
            snapshotFlow { gesture.active to gesture.progress }.collect { (active, swipe) ->
                if (active) {
                    following = true
                    if (isVisible) progress.snapTo(BackMath.containerOpen(swipe))
                } else if (following) {
                    following = false
                    if (!gesture.committed && isVisible) scope.launch { progress.animateTo(1f, Motion.spatial()) }
                }
            }
        }
    }
    LaunchedEffect(visible) {
        if (visible) gone = false
        progress.animateTo(if (visible) 1f else 0f, Motion.spatial())
        if (!visible) {
            gone = true
            onGone()
        }
    }
    if (gone && !visible) return
    val backPose = remember(gesture) {
        BackPose({ 1f - progress.value }, { if (gesture?.fromRight == true) -1f else 1f })
    }
    val from = origin.bounds
    val startCorners = origin.corners
    Box(
        modifier.graphicsLayer {
            val w = size.width
            val h = size.height
            transformOrigin = TransformOrigin(0f, 0f)
            val p = progress.value
            if (from != null && w > 0f && h > 0f) {
                val box = BackMath.container(p, from.left, from.top, from.width, from.height, w, h)
                scaleX = box.scaleX
                scaleY = box.scaleY
                translationX = box.x
                translationY = box.y
                val r = lerp(startCorners.topStart, endCorners.topStart, p.coerceIn(0f, 1f))
                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                    (r.toPx() / ((box.scaleX + box.scaleY) / 2f)).coerceAtLeast(0f),
                )
                clip = true
            } else {
                alpha = p.coerceIn(0f, 1f)
            }
        }.background(background),
    ) {
        // The surface behind stays solid; only the content comes in as the container opens and goes as it closes.
        Box(Modifier.graphicsLayer { alpha = (progress.value / 0.3f).coerceIn(0f, 1f) }) {
            CompositionLocalProvider(LocalBackPose provides backPose) { content() }
        }
    }
}

/** Colour that crossfades with the effects spring, for state changes (selected, enabled, pressed). */
@Composable
fun animateTone(target: androidx.compose.ui.graphics.Color, label: String = "tone") =
    animateColorAsState(target, Motion.effects(), label = label).value

