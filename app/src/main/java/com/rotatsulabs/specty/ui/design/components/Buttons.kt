package com.rotatsulabs.specty.ui.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes

enum class ButtonKind { Filled, Tonal, Outlined, Text }

/** Button with a pill silhouette that squares off and squashes while pressed. */
@Composable
fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    corners: Corners = SpectyShapes.Pill,
) {
    val s = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        ButtonKind.Filled -> s.primary to s.onPrimary
        ButtonKind.Tonal -> s.secondaryContainer to s.onSecondaryContainer
        ButtonKind.Outlined, ButtonKind.Text -> Color.Transparent to s.primary
    }
    val alpha = if (enabled) 1f else 0.38f
    MorphSurface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        corners = corners,
        color = container.copy(alpha = container.alpha * alpha),
        contentColor = content.copy(alpha = alpha),
        border = if (kind == ButtonKind.Outlined) BorderStroke(1.dp, s.outline) else null,
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
    ) {
        Row(
            Modifier.padding(horizontal = if (icon != null) 20.dp else 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, null, Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Round icon button; [toggled] swaps to the selected colours and a rounder-square shape. */
@Composable
fun ExpressiveIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    toggled: Boolean = false,
    tonal: Boolean = true,
    size: Dp = 48.dp,
) {
    val s = MaterialTheme.colorScheme
    val container = when {
        toggled -> s.primary
        tonal -> s.surfaceContainerHighest
        else -> Color.Transparent
    }
    val content = if (toggled) s.onPrimary else s.onSurfaceVariant
    MorphSurface(
        modifier = modifier.size(size).semantics { contentDescription = description },
        corners = if (toggled) Corners(size * 0.34f) else Corners(size),
        color = container,
        contentColor = content,
        onClick = onClick,
        role = Role.Button,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(size * 0.5f)) }
    }
}

/** Small label capsule; the colour pair is chosen by the caller so it works for verdicts, tags and counts alike. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    icon: ImageVector? = null,
) {
    MorphSurface(modifier, SpectyShapes.Pill, container, content) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

