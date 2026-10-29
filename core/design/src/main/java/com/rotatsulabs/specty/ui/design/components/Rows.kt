package com.rotatsulabs.specty.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes

/** Rounded icon badge used at the start of rows and headers. */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    corners: Corners = Corners(16.dp),
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    MorphSurface(modifier.size(size), corners, container, content, morphOnPress = false) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.5f)) }
    }
}

/**
 * A general list row: optional leading badge, title and supporting text, and a trailing slot.
 * Use [position] inside a [RowGroup] so neighbouring rows join into one segmented block.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    corners: Corners = SpectyShapes.Row,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    MorphSurface(modifier.fillMaxWidth(), corners, color, onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                leading != null -> { leading(); Spacer(Modifier.width(14.dp)) }
                icon != null -> { IconBadge(icon); Spacer(Modifier.width(14.dp)) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        }
    }
}

/** Corners for item [index] of [count] in a joined group: large at the ends, tight between neighbours. */
fun groupCorners(index: Int, count: Int, outer: Corners = SpectyShapes.Tile, inner: androidx.compose.ui.unit.Dp = 8.dp): Corners {
    val first = index == 0
    val last = index == count - 1
    return Corners(
        topStart = if (first) outer.topStart else inner,
        topEnd = if (first) outer.topEnd else inner,
        bottomEnd = if (last) outer.bottomEnd else inner,
        bottomStart = if (last) outer.bottomStart else inner,
    )
}

/** A titled stack of rows. Give each row `groupCorners(i, n)` so they read as one segmented block. */
@Composable
fun SettingGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
    }
}

/** Switch row with title and supporting text. Pass [corners] from [GroupScope.row]. */
@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    icon: ImageVector? = null,
    corners: Corners = SpectyShapes.Row,
    enabled: Boolean = true,
) {
    ListRow(
        title = title,
        supporting = supporting,
        icon = icon,
        corners = corners,
        modifier = modifier,
        onClick = if (enabled) ({ onChecked(!checked) }) else null,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** A row that hosts any control (a segmented control, a slider) beneath its title. */
@Composable
fun SettingBlock(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    corners: Corners = SpectyShapes.Row,
    content: @Composable () -> Unit,
) {
    MorphSurface(modifier.fillMaxWidth(), corners, MaterialTheme.colorScheme.surfaceContainer, morphOnPress = false) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
