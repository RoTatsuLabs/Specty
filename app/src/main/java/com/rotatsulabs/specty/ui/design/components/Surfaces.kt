package com.rotatsulabs.specty.ui.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.components.pressScale
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes
import androidx.compose.animation.core.animateFloatAsState
import com.rotatsulabs.specty.ui.theme.Motion
import com.rotatsulabs.specty.ui.design.animateTone

/**
 * The base of every container in the app. Its corners are a [Corners] value, so the surface can morph: when it is
 * clickable it squares its corners and squashes slightly while pressed, then springs back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MorphSurface(
    modifier: Modifier = Modifier,
    corners: Corners = SpectyShapes.Card,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(color),
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    role: Role? = null,
    morphOnPress: Boolean = true,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    /** Crossfade colour changes (toggles, selection). Static surfaces skip the animation, which matters in long lists. */
    animateColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // One spring drives all four corners, so a pressed surface costs a single animation.
    val squash by animateFloatAsState(if (pressed && morphOnPress && onClick != null) 1f else 0f, Motion.spatialFast(), label = "morph")
    val shape = remember(corners, squash) { corners.lerpTo(corners.pressed(), squash).toShape() }
    var m = modifier
    if (onClick != null) m = m.pressScale(source, 0.97f)
    if (shadowElevation > 0.dp) m = m.shadow(shadowElevation, shape)
    m = m.clip(shape).background(if (animateColor) animateTone(color) else color, shape)
    if (border != null) m = m.border(border, shape)
    if (onClick != null) {
        m = m.combinedClickable(
            interactionSource = source,
            indication = ripple(),
            enabled = enabled,
            role = role,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(m) { content() }
    }
}

/** Looks up an on-colour for a container colour used as a surface. */
private fun androidx.compose.material3.ColorScheme.contentColorFor(c: Color): Color = when (c) {
    primary -> onPrimary
    secondary -> onSecondary
    tertiary -> onTertiary
    primaryContainer -> onPrimaryContainer
    secondaryContainer -> onSecondaryContainer
    tertiaryContainer -> onTertiaryContainer
    errorContainer -> onErrorContainer
    error -> onError
    inverseSurface -> inverseOnSurface
    else -> onSurface
}

/** A padded [MorphSurface] holding a column: the standard card of the app. */
@Composable
fun SpectyCard(
    modifier: Modifier = Modifier,
    corners: Corners = SpectyShapes.Card,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    padding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    MorphSurface(modifier, corners, color, onClick = onClick) {
        Column(Modifier.padding(padding), content = content)
    }
}
