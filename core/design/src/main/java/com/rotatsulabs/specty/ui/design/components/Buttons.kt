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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.selected
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

/** The four button styles of Material 3 Expressive. Every one morphs its shape while pressed. */
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
    val shapes = if (corners == SpectyShapes.Pill) ButtonDefaults.shapes() else ButtonDefaults.shapes(corners.toShape(), corners.toShape())
    val padding = if (icon != null) ButtonDefaults.ButtonWithIconContentPadding else ButtonDefaults.ContentPadding
    val label: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(text, maxLines = 1, softWrap = false)
    }
    val sized = modifier.heightIn(min = ButtonDefaults.MinHeight)
    when (kind) {
        ButtonKind.Filled -> Button(onClick = onClick, shapes = shapes, modifier = sized, enabled = enabled, contentPadding = padding, content = label)
        ButtonKind.Tonal -> FilledTonalButton(onClick = onClick, shapes = shapes, modifier = sized, enabled = enabled, contentPadding = padding, content = label)
        ButtonKind.Outlined -> OutlinedButton(onClick = onClick, shapes = shapes, modifier = sized, enabled = enabled, contentPadding = padding, content = label)
        ButtonKind.Text -> TextButton(onClick = onClick, shapes = shapes, modifier = sized, enabled = enabled, content = label)
    }
}

/** Round icon button; [toggled] fills it with the primary colour. The shape morphs while pressed. */
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
    val body: @Composable () -> Unit = {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (scaleIn(Motion.spatialFast(), initialScale = 0.3f) + fadeIn(Motion.effects())) togetherWith
                    (scaleOut(Motion.effects(), targetScale = 0.3f) + fadeOut(Motion.effects()))
            },
            label = "iconSwap",
        ) { current -> Icon(current, null, iconModifier.size(size * 0.5f)) }
    }
    val sized = modifier.size(size).semantics { contentDescription = description; selected = toggled }
    val shapes = IconButtonDefaults.shapes()
    when {
        toggled -> FilledIconButton(onClick = onClick, shapes = shapes, modifier = sized, content = body)
        tonal -> FilledTonalIconButton(onClick = onClick, shapes = shapes, modifier = sized, content = body)
        else -> IconButton(onClick = onClick, shapes = shapes, modifier = sized, content = body)
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
