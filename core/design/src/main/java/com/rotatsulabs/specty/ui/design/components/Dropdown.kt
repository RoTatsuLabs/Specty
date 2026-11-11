package com.rotatsulabs.specty.ui.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.theme.Motion
import com.rotatsulabs.specty.ui.design.Spacing
import com.rotatsulabs.specty.ui.design.animateTone

/**
 * A choice picker. The closed state is a tonal capsule with a chevron that turns over as it opens; the menu is a
 * rounded sheet as wide as the capsule, with the current choice marked by a filled, check-ended row.
 */
@Composable
fun <T> OptionDropdown(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var width by remember { mutableStateOf(0) }
    val scheme = MaterialTheme.colorScheme
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.spatial(), label = "dropdownChevron")
    Box(modifier.fillMaxWidth()) {
        MorphSurface(
            modifier = Modifier.fillMaxWidth().onSizeChanged { width = it.width },
            corners = Corners(28.dp),
            color = if (open) scheme.secondaryContainer else scheme.surfaceContainerHighest,
            animateColor = true,
            role = Role.DropdownList,
            onClick = { open = !open },
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Spacing.Xl - Spacing.Xs, end = Spacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label(selected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = animateTone(if (open) scheme.secondary else scheme.surfaceContainerHigh, label = "dropdownChevronBg"),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = if (open) scheme.onSecondary else scheme.onSurfaceVariant,
                            modifier = Modifier.rotate(turn),
                        )
                    }
                }
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(with(LocalDensity.current) { width.toDp() }),
            offset = DpOffset(0.dp, Spacing.Sm),
            shape = RoundedCornerShape(28.dp),
            containerColor = scheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            options.forEach { option ->
                val current = option == selected
                MorphSurface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Sm, vertical = 2.dp),
                    corners = Corners(if (current) 22.dp else 16.dp),
                    color = if (current) scheme.secondaryContainer else Color.Transparent,
                    role = Role.Button,
                    onClick = { open = false; if (!current) onSelect(option) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = Spacing.Lg),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
                    ) {
                        Text(label(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (current) Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onSecondaryContainer)
                    }
                }
            }
        }
    }
}
