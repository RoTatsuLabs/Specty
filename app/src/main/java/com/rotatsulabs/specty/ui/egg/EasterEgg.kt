package com.rotatsulabs.specty.ui.egg

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.sin

private const val HOLD_MILLIS = 5000
private const val RECORD_FROM = 0.85f

/**
 * Easter egg: holding the Specty logo shakes the whole screen harder for five seconds, then the screen shatters
 * like glass and a Flappy Bird game takes its place.
 */
@Stable
class EasterEgg {
    /** 0 to 1 over the five second hold; eases back to 0 on release. */
    val hold = Animatable(0f)
    var holding by mutableStateOf(false)

    /** Shake phase in radians; integrated from the current frequency so speeding up never makes it jump. */
    val phase = mutableFloatStateOf(0f)
    var impact by mutableStateOf(Offset.Zero)
    var snapshot by mutableStateOf<ImageBitmap?>(null)

    /** The shattering glass is on screen. */
    var glass by mutableStateOf(false)

    /** The game is on screen (it appears under the falling shards). */
    var game by mutableStateOf(false)

    val busy: Boolean get() = glass || game

    internal fun advance(dt: Float) {
        val k = hold.value * hold.value
        val hz = 2.5f + 12f * k
        phase.floatValue = (phase.floatValue + dt * hz * 2f * PI.toFloat()) % (200f * PI.toFloat())
    }

    fun close() {
        game = false
        glass = false
        snapshot = null
    }
}

/** Marks the logo as the hold target; also remembers where it is, so the glass cracks outwards from it. */
fun Modifier.eggHold(egg: EasterEgg): Modifier = this
    .onGloballyPositioned { egg.impact = it.boundsInRoot().center }
    .pointerInput(egg) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            if (egg.busy) return@awaitEachGesture
            egg.holding = true
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    // Lifting the finger, or a scroll taking over the touch, ends the hold.
                    if (event.changes.none { it.pressed } || event.changes.any { it.isConsumed }) break
                }
            } finally {
                egg.holding = false
            }
        }
    }

/** Shakes the content: starts at rest, then grows in size and speed with the hold. */
fun Modifier.eggShake(egg: EasterEgg): Modifier = graphicsLayer {
    val k = egg.hold.value * egg.hold.value
    val p = egg.phase.floatValue
    val amplitude = k * 16.dp.toPx()
    translationX = amplitude * (sin(p) + 0.55f * sin(p * 2.37f + 1.3f)) / 1.55f
    translationY = amplitude * (sin(p * 1.31f + 0.7f) + 0.55f * sin(p * 2.91f + 2.1f)) / 1.55f
    rotationZ = k * 1.6f * sin(p * 0.83f + 2f)
    val scale = 1f + 0.015f * k
    scaleX = scale
    scaleY = scale
}

/** Records the content into [layer] near the end of the hold, so the glass has a picture of the screen to break. */
fun Modifier.eggCapture(egg: EasterEgg, layer: GraphicsLayer): Modifier = drawWithContent {
    if (egg.hold.value > RECORD_FROM) {
        layer.record {
            this@drawWithContent.drawContent()
        }
        drawLayer(layer)
    } else {
        drawContent()
    }
}

/** Runs the hold, the shake clock and the hand-over to the glass. Draws nothing. */
@Composable
fun EasterEggDriver(egg: EasterEgg, layer: GraphicsLayer) {
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(egg.holding) {
        if (egg.holding) {
            val remaining = ((1f - egg.hold.value) * HOLD_MILLIS).toInt().coerceAtLeast(1)
            egg.hold.animateTo(1f, tween(remaining, easing = LinearEasing))
            if (egg.hold.value >= 1f) {
                // One more frame, so the last draw has recorded the layer before it is read.
                withFrameNanos { }
                val picture = withTimeoutOrNull(1500) { layer.toImageBitmap() }
                if (picture != null) {
                    egg.snapshot = picture
                    egg.glass = true
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                egg.hold.snapTo(0f)
            }
        } else {
            egg.hold.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(egg) {
        snapshotFlow { egg.hold.value > 0f }.collectLatest { active ->
            if (!active) return@collectLatest
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                egg.advance(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
            }
        }
    }
}

/** The shattering glass and the game underneath it. Place it last in the root so it covers everything. */
@Composable
fun EasterEggStage(egg: EasterEgg) {
    BackHandler(enabled = egg.game) { egg.close() }

    AnimatedVisibility(
        visible = egg.game,
        enter = fadeIn(tween(60)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 120f), initialScale = 1.12f),
        exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.94f),
    ) {
        FlappyGame(onExit = egg::close)
    }

    val picture = egg.snapshot
    if (egg.glass && picture != null) {
        GlassBreak(
            snapshot = picture,
            impact = egg.impact,
            onShatter = { egg.game = true },
            onDone = {
                egg.glass = false
                egg.snapshot = null
            },
        )
    }
}
