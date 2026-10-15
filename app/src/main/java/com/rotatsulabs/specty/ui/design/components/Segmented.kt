package com.rotatsulabs.specty.ui.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.animateTone
import com.rotatsulabs.specty.ui.theme.Motion

/** One entry of a [SegmentedControl]. */
data class Segment<T>(val value: T, val label: String, val icon: ImageVector? = null, val badge: String? = null)

/**
 * A segmented control whose selected pill slides between segments on a spring and stretches to fit each label.
 * With [scrollable] the segments take their natural width and the row scrolls horizontally, which suits long lists
 * (palettes, FFT sizes); otherwise segments share the width equally.
 */
@Composable
fun <T> SegmentedControl(
    segments: List<Segment<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    height: Dp = if (segments.any { it.badge != null }) 60.dp else 48.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    indicator: Color = MaterialTheme.colorScheme.primary,
    onIndicator: Color = MaterialTheme.colorScheme.onPrimary,
    onTrack: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val density = LocalDensity.current
    val lefts = remember { mutableStateMapOf<Int, Float>() }
    val widths = remember { mutableStateMapOf<Int, Float>() }
    val index = segments.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val targetLeft = with(density) { (lefts[index] ?: 0f).toDp() }
    val targetWidth = with(density) { (widths[index] ?: 0f).toDp() }
    val left by animateDpAsState(targetLeft, Motion.spatial(), label = "segLeft")
    val width by animateDpAsState(targetWidth, Motion.spatial(), label = "segWidth")
    val shape = SpectyShapes.Pill.toShape()
    val pad = 4.dp

    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(track, shape)
            .padding(pad),
    ) {
        val inner = if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()
        Box(inner.fillMaxHeight()) {
        if (targetWidth > 0.dp) {
            Box(
                Modifier
                    .offset { IntOffset(left.roundToPx(), 0) }
                    .width(width)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(indicator, shape),
            )
        }
        Row((if (scrollable) Modifier else Modifier.fillMaxWidth()).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
            segments.forEachIndexed { i, seg ->
                val active = i == index
                val contentColor = animateTone(if (active) onIndicator else onTrack)
                Row(
                    Modifier
                        .then(if (scrollable) Modifier else Modifier.weight(1f))
                        .fillMaxHeight()
                        .onGloballyPositioned {
                            // Positions are relative to the row, which starts at the track's inner edge.
                            lefts[i] = it.positionInParent().x
                            widths[i] = it.size.width.toFloat()
                        }
                        .clip(shape)
                        .semantics { this.selected = active }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                        ) { onSelect(seg.value) }
                        .padding(horizontal = if (segments.any { it.badge != null }) 6.dp else 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (seg.icon != null) {
                        Icon(seg.icon, null, tint = contentColor, modifier = Modifier.width(18.dp).height(18.dp))
                        Box(Modifier.width(6.dp))
                    }
                    if (seg.badge == null) {
                        Text(
                            seg.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        // Label over a small count, so several segments with counts still fit one row.
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(seg.label, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(seg.badge, style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.72f), maxLines = 1)
                        }
                    }
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
    label: (T) -> String,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) = SegmentedControl(options.map { Segment(it, label(it)) }, selected, onSelect, modifier, scrollable)
