package com.rotatsulabs.specty.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.rotatsulabs.specty.ui.theme.Motion
import kotlinx.coroutines.delay

/** Springy squash while the element is pressed, released with a small overshoot. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.92f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.spatialFast(), label = "pressScale")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Fades and lifts [content] into place, staggered by [index]. */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(70L * index)
        shown = true
    }
    val p by animateFloatAsState(if (shown) 1f else 0f, Motion.spatial(), label = "reveal")
    Box(
        modifier.graphicsLayer {
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * 72f
        },
    ) { content() }
}
