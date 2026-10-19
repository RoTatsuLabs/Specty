package com.rotatsulabs.specty.ui.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.animateTone
import com.rotatsulabs.specty.ui.design.tonal.SeedPalette
import com.rotatsulabs.specty.ui.design.tonal.SeedScheme
import com.rotatsulabs.specty.ui.theme.Motion

/** Search box with an asymmetric, softly morphing outline and a clear button that springs in. */
@Composable
fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_default),
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val radius by animateDpAsState(if (focused) 18.dp else 32.dp, Motion.spatial(), label = "searchRadius")
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 14.dp, topEnd = radius, bottomEnd = radius, bottomStart = radius,
    )
    val container = animateTone(
        if (focused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
    )
    Row(
        modifier.fillMaxWidth().clip(shape).background(container, shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (query.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                interactionSource = source,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AnimatedVisibility(query.isNotEmpty(), enter = scaleIn(Motion.spatialFast()) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(
                Icons.Rounded.Close, stringResource(R.string.search_clear),
                Modifier.size(24.dp).clip(CircleShape).clickable { onChange("") },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Row of round colour swatches for picking a tonal palette; the selected one grows a check and a ring. */
@Composable
fun SwatchRow(
    selected: SeedPalette,
    onSelect: (SeedPalette) -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    /** Also told where the swatch is on screen, so a colour change can spread out from it. */
    onSelectAt: ((SeedPalette, androidx.compose.ui.geometry.Offset) -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SeedPalette.entries.forEach { seed ->
            val roles = remember(seed, dark) { SeedScheme(seed).roles(dark) }
            val active = seed == selected
            val size by animateDpAsState(if (active) 56.dp else 48.dp, Motion.spatialFast(), label = "swatch")
            var center by remember(seed) { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Unspecified) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) { if (onSelectAt != null) onSelectAt(seed, center) else onSelect(seed) }) {
                Box(
                    Modifier.size(58.dp).onGloballyPositioned { center = it.boundsInRoot().center },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(size)
                            .clip(CircleShape)
                            .background(Color(roles.primaryContainer))
                            .then(if (active) Modifier.border(3.dp, Color(roles.primary), CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(size * 0.5f).clip(CircleShape).background(Color(roles.primary)))
                        androidx.compose.animation.AnimatedVisibility(active, enter = scaleIn(Motion.spatialFast()), exit = scaleOut()) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = Color(roles.onPrimary))
                        }
                    }
                }
                Text(stringResource(seed.labelRes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
