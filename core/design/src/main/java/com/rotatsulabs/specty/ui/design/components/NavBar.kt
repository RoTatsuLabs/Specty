package com.rotatsulabs.specty.ui.design.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** One destination of the bottom bar. [icon] is shown when it is not selected and [selectedIcon] when it is. */
class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/**
 * The bottom bar. Labels are always visible, the selected destination sits on a pill, and every item is a tab for
 * screen readers.
 */
@Composable
fun SpectyNavigationBar(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(modifier, containerColor = scheme.surfaceContainer, tonalElevation = androidx.compose.ui.unit.Dp.Hairline) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = index == selected,
                onClick = { onSelect(index) },
                icon = { Icon(if (index == selected) item.selectedIcon else item.icon, contentDescription = null) },
                label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = scheme.secondaryContainer,
                    selectedIconColor = scheme.onSecondaryContainer,
                    selectedTextColor = scheme.onSurface,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                ),
            )
        }
    }
}
