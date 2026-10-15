package com.rotatsulabs.specty.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes

/** A big value over a small label, on a tonal container: the unit of every numbers grid. */
@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    corners: Corners = SpectyShapes.Tile,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    MorphSurface(modifier, corners, container, content, onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.72f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = MaterialTheme.typography.headlineSmall)
                if (unit != null) {
                    Text(unit, style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.72f), modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

/** Equal-width tiles in one row. */
@Composable
fun MetricRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
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
        Verdict.INCONCLUSIVE -> null
    }
}

@Composable
fun VerdictPill(verdict: Verdict, modifier: Modifier = Modifier) {
    val style = verdictStyle(verdict) ?: return
    Pill(style.label, modifier, style.container, style.content)
}
