package com.rotatsulabs.specty.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.Spacing
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.animateTone

/** One destination of the bottom bar. [icon] is shown when it is not selected and [selectedIcon] when it is. */
class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * The bottom bar: one row with two floating pills. The first [items] sit together in the main pill; the last
 * [separated] items get a pill of their own. [selected] counts across both pills.
 */
@Composable
fun SpectyNavigationBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    separated: Int = 0,
) {
    val split = items.size - separated
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavPill(Modifier.weight(1f)) {
            for (i in 0 until split) NavDestination(items[i], i == selected, { onSelect(i) }, Modifier.weight(1f))
        }
        if (separated > 0) {
            NavPill {
                for (i in split until items.size) NavDestination(items[i], i == selected, { onSelect(i) }, Modifier.width(76.dp))
            }
        }
    }
}

@Composable
private fun NavPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier,
        shape = SpectyShapes.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(Spacing.Xs), horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) { content() }
    }
}

@Composable
private fun NavDestination(item: NavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = animateTone(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh, label = "navContainer")
    val content = animateTone(if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, label = "navContent")
    Box(
        modifier
            .heightIn(min = Spacing.MinTarget + Spacing.Sm)
            .clip(SpectyShapes.Pill)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Surface(Modifier.matchParentSize(), shape = SpectyShapes.Pill, color = container) {}
        Column(Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.Xs), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null, tint = content)
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
