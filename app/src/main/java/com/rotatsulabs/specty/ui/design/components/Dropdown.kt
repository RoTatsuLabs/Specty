package com.rotatsulabs.specty.ui.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.theme.Motion

/** A field showing the current choice that opens a menu of all of them; the chevron turns while it is open. */
@Composable
fun <T> OptionDropdown(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var width by remember { mutableIntStateOf(0) }
    val turn by animateFloatAsState(if (open) 180f else 0f, Motion.spatial(), label = "chevron")
    Box(modifier.fillMaxWidth().onSizeChanged { width = it.width }) {
        MorphSurface(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
            Corners(16.dp),
            MaterialTheme.colorScheme.surfaceContainerHighest,
            onClick = { open = !open },
            role = Role.DropdownList,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label(selected), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(24.dp).rotate(turn))
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.width(with(LocalDensity.current) { width.toDp() }),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        ) {
            options.forEach { option ->
                val active = option == selected
                ExpressiveMenuItem(
                    text = { Text(label(option), fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) },
                    trailingIcon = if (active) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    onClick = { open = false; if (!active) onSelect(option) },
                )
            }
        }
    }
}

