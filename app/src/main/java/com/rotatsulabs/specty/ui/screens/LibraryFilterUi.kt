package com.rotatsulabs.specty.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.EmptyState
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton

@get:StringRes
private val ResultFilter.labelRes: Int
    get() = when (this) {
        ResultFilter.Unchecked -> R.string.filter_unchecked
        ResultFilter.Lossless -> R.string.filter_lossless
        ResultFilter.HiRes -> R.string.verdict_hires
        ResultFilter.NeedsLook -> R.string.filter_needs_look
        ResultFilter.LossyFormat -> R.string.filter_lossy_format
        ResultFilter.Atmos -> R.string.filter_atmos
        ResultFilter.Unclear -> R.string.filter_unclear
    }

@get:StringRes
private val ResultFilter.hintRes: Int
    get() = when (this) {
        ResultFilter.Unchecked -> R.string.filter_unchecked_hint
        ResultFilter.Lossless -> R.string.filter_lossless_hint
        ResultFilter.HiRes -> R.string.filter_hires_hint
        ResultFilter.NeedsLook -> R.string.filter_needs_look_hint
        ResultFilter.LossyFormat -> R.string.filter_lossy_format_hint
        ResultFilter.Atmos -> R.string.filter_atmos_hint
        ResultFilter.Unclear -> R.string.filter_unclear_hint
    }

/** Opens the filter sheet. A small badge says how many kinds are chosen, so an active filter is never a surprise. */
@Composable
internal fun FilterButton(active: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Box(modifier) {
        ExpressiveIconButton(
            Icons.Rounded.Tune,
            stringResource(R.string.filter_open),
            { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() },
            toggled = active > 0,
        )
        if (active > 0) {
            Badge(Modifier.align(Alignment.TopEnd)) { Text("$active") }
        }
    }
}

/** The chosen kinds as chips under the search field, each removable in one tap. Hidden when nothing is chosen. */
@Composable
internal fun ActiveFilters(
    selected: Set<ResultFilter>,
    onRemove: (ResultFilter) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = selected.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (f in ResultFilter.entries) {
                if (f !in selected) continue
                InputChip(
                    selected = true,
                    onClick = { onRemove(f) },
                    label = { Text(stringResource(f.labelRes)) },
                    trailingIcon = { Icon(Icons.Rounded.Close, stringResource(R.string.filter_remove, stringResource(f.labelRes)), Modifier.size(18.dp)) },
                )
            }
            TextButton(onClick = onClear) { Text(stringResource(R.string.filter_clear)) }
        }
    }
}

/**
 * Every kind of result with what it means and how many tracks have it. Rows with no tracks stay visible but
 * disabled, so the list of kinds never changes shape. The button shows how many tracks the choice leaves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheet(
    selected: Set<ResultFilter>,
    counts: Map<ResultFilter, Int>,
    shown: Int,
    onToggle: (ResultFilter) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.filter_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            for (f in ResultFilter.entries) {
                val count = counts[f] ?: 0
                val checked = f in selected
                val enabled = count > 0 || checked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(SpectyShapes.Row.toShape())
                        .toggleable(checked, enabled = enabled, role = Role.Checkbox) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggle(f)
                        }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(f.labelRes),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                        Text(stringResource(f.hintRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "$count",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClear, enabled = selected.isNotEmpty()) { Text(stringResource(R.string.filter_reset)) }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(pluralStringResource(R.plurals.filter_show, shown, shown))
                }
            }
        }
    }
}

/** Shown when the chosen kinds leave nothing: says why and offers the way out. */
@Composable
internal fun FilterEmpty(onClear: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Rounded.FilterAltOff,
        title = stringResource(R.string.filter_empty_title),
        message = stringResource(R.string.filter_empty_message),
        modifier = modifier,
        action = { ExpressiveButton(stringResource(R.string.filter_clear), onClear, kind = ButtonKind.Tonal) },
    )
}
