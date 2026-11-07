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
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
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

/** Linear progress as the Material 3 Expressive wavy bar. [progress] null means indeterminate. */
@Composable
fun WavyProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    if (progress == null) {
        LinearWavyProgressIndicator(modifier.fillMaxWidth(), color = color, trackColor = track)
    } else {
        val animated = animateFloatAsState(progress.coerceIn(0f, 1f), Motion.spatial(), label = "progress")
        LinearWavyProgressIndicator(progress = { animated.value }, modifier = modifier.fillMaxWidth(), color = color, trackColor = track)
    }
}

/** Circular progress as the Material 3 Expressive wavy ring, with optional centre content. */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    center: (@Composable () -> Unit)? = null,
) {
    val p = animateFloatAsState(progress.coerceIn(0f, 1f), Motion.spatial(), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(progress = { p.value }, modifier = Modifier.size(size), color = color, trackColor = track)
        center?.invoke()
    }
}

/** Busy indicator: the Material 3 Expressive loading indicator, a shape that morphs between polygons. */
@Composable
fun ShapeLoader(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LoadingIndicator(modifier.size(size), color = color)
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
