package com.rotatsulabs.specty.ui.design.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow

/** One entry of a [SegmentedControl]. */
data class Segment<T>(val value: T, val label: String, val icon: ImageVector? = null, val badge: String? = null)

/**
 * A connected button group with one selected button, the Material 3 Expressive replacement for a segmented control.
 * With [scrollable] the buttons take their natural width and the row scrolls sideways, which suits long lists
 * (palettes, FFT sizes); otherwise they share the width equally.
 */
@Composable
fun <T> SegmentedControl(
    segments: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val row = if (scrollable) modifier.horizontalScroll(rememberScrollState()) else modifier.fillMaxWidth()
    Row(row, horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        segments.forEachIndexed { i, seg ->
            val shapes = when {
                i == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                i == segments.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = seg.value == selected,
                onCheckedChange = { if (it) onSelect(seg.value) },
                modifier = if (scrollable) Modifier else Modifier.weight(1f),
                shapes = shapes,
            ) {
                if (seg.icon != null) {
                    Icon(seg.icon, null, Modifier.size(ToggleButtonDefaults.IconSize))
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                if (seg.badge == null) {
                    Text(seg.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    // Label over a small count, so several segments with counts still fit one row.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(seg.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(seg.badge, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Convenience for the common case of an enum or list of labelled values. */
@Composable
fun <T> OptionSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) = SegmentedControl(options.map { Segment(it, label(it)) }, selected, onSelect, modifier, scrollable)
