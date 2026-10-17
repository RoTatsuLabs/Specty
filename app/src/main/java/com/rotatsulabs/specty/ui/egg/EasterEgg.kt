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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.rotatsulabs.specty.sound.Fx
import com.rotatsulabs.specty.sound.SoundFx
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val HOLD_MILLIS = 5000
private const val RECORD_FROM = 0.85f

/**
 * Easter egg: holding the Specty logo makes every item on screen shake harder for five seconds, then the screen shatters
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

/** The running egg, for components that shake on their own. Null outside the app root. */
val LocalEasterEgg = staticCompositionLocalOf<EasterEgg?> { null }

/**
 * Shakes this component by itself: every [seed] gets its own rhythm and direction, so items jostle independently.
 * Starts at rest and grows in size and speed with the hold.
 */
@Composable
fun Modifier.eggJitter(seed: Int): Modifier {
    val egg = LocalEasterEgg.current ?: return this
    val shape = remember(seed) {
        val r = Random(seed * 7919 + 13)
        floatArrayOf(
            r.nextFloat() * 6.28f, r.nextFloat() * 6.28f, r.nextFloat() * 6.28f,
            0.8f + r.nextFloat() * 0.5f, 0.8f + r.nextFloat() * 0.5f,
            if (r.nextBoolean()) 1f else -1f,
        )
    }
    return graphicsLayer {
        val k = egg.hold.value * egg.hold.value
        val p = egg.phase.floatValue
        val amplitude = k * 9.dp.toPx()
        translationX = amplitude * (sin(p * shape[3] + shape[0]) + 0.5f * sin(p * 2.3f + shape[1])) / 1.5f
        translationY = amplitude * (sin(p * shape[4] + shape[1]) + 0.5f * sin(p * 2.7f + shape[2])) / 1.5f
        rotationZ = shape[5] * k * 1.8f * sin(p * 0.9f + shape[2])
    }
}

/** The app root: provides the egg to [eggJitter] and records the content for the glass. */
@Composable
fun EasterEggBox(egg: EasterEgg, layer: GraphicsLayer, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalEasterEgg provides egg) {
        Box(Modifier.fillMaxSize().eggCapture(egg, layer), content = content)
    }
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
                    SoundFx.play(Fx.Glass)
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
            if (!active) {
                SoundFx.rumble(0f)
                return@collectLatest
            }
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                egg.advance(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                SoundFx.rumble(egg.hold.value)
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
