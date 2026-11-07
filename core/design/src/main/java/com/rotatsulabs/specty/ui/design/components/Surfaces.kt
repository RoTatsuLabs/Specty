package com.rotatsulabs.specty.ui.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.animateTone

/**
 * The base of the app's simple containers, built on the Material [Surface]. Kept as one entry point so call sites
 * share a single place for the corner [Corners], colour and click handling (a long press needs
 * `combinedClickable`, which Material's clickable Surface has no overload for).
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
    @Suppress("UNUSED_PARAMETER") morphOnPress: Boolean = true,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
    /** Crossfade colour changes (toggles, selection). Static surfaces skip the animation, which matters in long lists. */
    animateColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = remember(corners) { corners.toShape() }
    val container = if (animateColor) animateTone(color) else color
    Surface(modifier, shape = shape, color = container, contentColor = contentColor, border = border, shadowElevation = shadowElevation) {
        val click = if (onClick != null) {
            Modifier.combinedClickable(enabled = enabled, role = role, onClick = onClick, onLongClick = onLongClick)
        } else {
            Modifier
        }
        Box(click) { content() }
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
    val shape = corners.toShape()
    val colors = CardDefaults.cardColors(containerColor = color)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}
