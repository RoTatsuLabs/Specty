package com.rotatsulabs.specty.ui.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import com.rotatsulabs.specty.ui.components.pressScale
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.rotatsulabs.specty.ui.theme.Motion

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
    // 48 dp is the minimum comfortable touch target; smaller sizes are for decorative use only.
    size: Dp = 48.dp,
    iconModifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    val container = when {
        toggled -> s.primary
        tonal -> s.surfaceContainerHighest
        else -> Color.Transparent
    }
    val content = if (toggled) s.onPrimary else s.onSurfaceVariant
    // The silhouette springs between round and rounded-square instead of jumping.
    val radius by animateDpAsState(if (toggled) size * 0.34f else size, Motion.spatial(), label = "iconCorner")
    MorphSurface(
        modifier = modifier.size(size).semantics { contentDescription = description },
        corners = Corners(radius),
        color = container,
        contentColor = content,
        onClick = onClick,
        role = Role.Button,
        animateColor = true,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = icon,
                transitionSpec = {
                    (scaleIn(Motion.spatialFast(), initialScale = 0.3f) + fadeIn(Motion.effects())) togetherWith
                        (scaleOut(Motion.effects(), targetScale = 0.3f) + fadeOut(Motion.effects()))
                },
                label = "iconSwap",
            ) { current -> Icon(current, null, iconModifier.size(size * 0.5f)) }
        }
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
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}


/** Menu row that squashes under the finger like the rest of the app's controls. */
@Composable
fun ExpressiveMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    /** Marks the current choice in a menu of options. */
    checked: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val trailing: (@Composable () -> Unit)? = if (checked) { { Icon(Icons.Rounded.Check, contentDescription = null) } } else trailingIcon
    DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier.pressScale(source, 0.96f),
        leadingIcon = leadingIcon,
        trailingIcon = trailing,
        enabled = enabled,
        interactionSource = source,
    )
}

/** Removable chip: a capsule that squashes when pressed, with a close icon. */
@Composable
fun ExpressiveChip(
    text: String,
    closeDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MorphSurface(
        modifier = modifier.defaultMinSize(minHeight = 32.dp),
        corners = SpectyShapes.Pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        onClick = onClick,
        role = Role.Button,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Icon(Icons.Rounded.Close, closeDescription, Modifier.size(18.dp))
        }
    }
}
