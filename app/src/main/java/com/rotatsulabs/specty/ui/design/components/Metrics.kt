package com.rotatsulabs.specty.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes

/**
 * A big value over a small label, on a tonal container: the unit of every numbers grid.
 * With [countUp] a numeric [value] counts up from zero once, keeping its decimal places.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    corners: Corners = SpectyShapes.Tile,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onSurface,
    countUp: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val target = if (countUp) value.toDoubleOrNull() else null
    val decimals = value.substringAfter('.', "").length
    val progress = remember(value) { Animatable(if (target == null) 1f else 0f) }
    if (target != null) {
        LaunchedEffect(value) {
            progress.animateTo(1f, tween(1100, delayMillis = 250, easing = FastOutSlowInEasing))
        }
    }
    val shown = if (target != null) String.format(Locale.US, "%.${decimals}f", target * progress.value) else value
    MorphSurface(modifier, corners, container, content, onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(shown, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            // A blank line keeps tiles without a unit the same height as their neighbours.
            Text(unit ?: " ", style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.72f), maxLines = 1)
        }
    }
}

/** Colours and label for a verdict, shared by badges, tiles and the dock so they always agree. */
data class VerdictStyle(val label: String, val container: Color, val content: Color)

@Composable
fun verdictStyle(verdict: Verdict): VerdictStyle? {
    val s = MaterialTheme.colorScheme
    return when (verdict) {
        Verdict.GENUINE -> VerdictStyle("Genuine", s.tertiaryContainer, s.onTertiaryContainer)
        Verdict.LOSSY_SOURCE, Verdict.UPSAMPLED -> VerdictStyle("Fake", s.errorContainer, s.onErrorContainer)
        Verdict.POSSIBLE_LOSSY_SOURCE, Verdict.BAND_LIMITED -> VerdictStyle("Maybe", s.secondaryContainer, s.onSecondaryContainer)
        Verdict.LOSSY_CODEC -> VerdictStyle("Lossy", s.surfaceContainerHighest, s.onSurfaceVariant)
        Verdict.DOLBY_ATMOS -> VerdictStyle("Dolby Atmos", s.inverseSurface, s.inverseOnSurface)
        Verdict.INCONCLUSIVE -> null
    }
}

@Composable
fun VerdictPill(verdict: Verdict, modifier: Modifier = Modifier) {
    val style = verdictStyle(verdict) ?: return
    Pill(style.label, modifier, style.container, style.content)
}
