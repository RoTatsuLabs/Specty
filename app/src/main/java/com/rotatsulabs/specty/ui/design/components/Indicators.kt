package com.rotatsulabs.specty.ui.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.LobedShape
import com.rotatsulabs.specty.ui.theme.Motion
import kotlin.math.PI
import kotlin.math.sin

/**
 * Linear progress drawn as a travelling sine wave over a flat track. [progress] null means indeterminate:
 * the wave keeps moving and fills nothing. The wave flattens near the end so completion reads as calm.
 */
@Composable
fun WavyProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    amplitude: Dp = 4.dp,
    wavelength: Dp = 28.dp,
    stroke: Dp = 5.dp,
) {
    val phase = rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val animated = animateFloatAsState(progress ?: 0.35f, Motion.spatial(), label = "progress")
    Canvas(modifier.fillMaxWidth().height(amplitude * 2 + stroke)) {
        val w = size.width
        val mid = size.height / 2f
        val strokePx = stroke.toPx()
        val amp = amplitude.toPx()
        val len = wavelength.toPx()
        val filled = w * animated.value.coerceIn(0f, 1f)
        val phaseNow = phase.value
        drawLine(track, Offset(filled, mid), Offset(w, mid), strokePx, StrokeCap.Round)
        val path = Path()
        var x = 0f
        while (x <= filled) {
            val damp = if (progress != null && progress > 0.9f) ((1f - progress) / 0.1f).coerceIn(0.2f, 1f) else 1f
            val y = mid + amp * damp * sin(x / len * 2f * PI.toFloat() - phaseNow)
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 2f
        }
        drawPath(path, color, style = Stroke(strokePx, cap = StrokeCap.Round))
    }
}

/** Circular progress: a ring that eases to [progress], with optional centre content. */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    stroke: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    center: (@Composable () -> Unit)? = null,
) {
    val p = animateFloatAsState(progress.coerceIn(0f, 1f), Motion.spatial(), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val inset = s / 2f
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * p.value, false, Offset(inset, inset), arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        center?.invoke()
    }
}

/** Busy indicator: a lobed shape that turns and breathes between a circle and a scalloped badge. */
@Composable
fun ShapeLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val t = rememberInfiniteTransition(label = "loader")
    val spin = t.animateFloat(0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "spin")
    val depth by t.animateFloat(0.04f, 0.16f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "depth")
    Box(
        modifier
            .size(size)
            .graphicsLayer { rotationZ = spin.value }
            .clip(LobedShape(lobes = 8, amplitude = depth))
            .background(color),
    )
}

/** Empty or error state: a large lobed badge with an icon, a title and a message, and an optional action. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(96.dp).clip(LobedShape(10, 0.08f)).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) action()
    }
}
