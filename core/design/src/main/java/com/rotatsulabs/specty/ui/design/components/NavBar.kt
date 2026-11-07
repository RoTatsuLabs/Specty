package com.rotatsulabs.specty.ui.design.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.foundation.shape.CircleShape
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
    /** While true the last item opens up into a text field and the rest fold into one button that returns to [backTo]. */
    searching: Boolean = false,
    query: String = "",
    onQuery: (String) -> Unit = {},
    placeholder: String = "",
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    backTo: Int = 0,
) {
    val split = items.size - separated
    // Hold a finger on the bar and slide it across the destinations: the highlight follows, and lifting selects.
    val bounds = remember { mutableStateMapOf<Int, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var preview by remember { mutableStateOf<Int?>(null) }
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(onSelect)
    val shown = preview ?: selected
    val dragging = preview != null

    fun hit(x: Float): Int? = bounds.entries.minByOrNull { (_, r) -> kotlin.math.abs(r.center.x - (x + origin.x)) }?.key

    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(items.size, searching) {
                if (searching) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var moved = false
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUp()) {
                            if (moved) { change.consume(); preview?.let { current(it) } }
                            break
                        }
                        if (!moved && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            moved = true
                            preview = hit(down.position.x)
                        }
                        if (moved) {
                            change.consume()
                            val index = hit(change.position.x)
                            if (index != null && index != preview) {
                                preview = index
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    }
                    preview = null
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = searching,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = { fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith fadeOut(spring(stiffness = Spring.StiffnessMedium)) },
            label = "navSearch",
        ) { open ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                if (open) {
                    // Everything but search folds into one button; search stretches across the rest of the row.
                    NavPill {
                        NavDestination(items[backTo], false, false, false, { current(backTo) }, Modifier.width(76.dp))
                    }
                    NavPill(Modifier.weight(1f)) {
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f).heightIn(min = Spacing.MinTarget + Spacing.Sm), contentAlignment = Alignment.CenterStart) {
                            SearchField(query, onQuery, Modifier.fillMaxWidth(), placeholder = placeholder, focusRequester = focusRequester)
                        }
                    }
                } else {
                    NavPill(Modifier.weight(1f)) {
                        for (i in 0 until split) {
                            NavDestination(items[i], i == selected, i == shown, dragging, { current(i) }, Modifier.weight(1f).onGloballyPositioned { bounds[i] = it.boundsInRoot() })
                        }
                    }
                    if (separated > 0) {
                        NavPill {
                            for (i in split until items.size) {
                                NavDestination(items[i], i == selected, i == shown, dragging, { current(i) }, Modifier.width(76.dp).onGloballyPositioned { bounds[i] = it.boundsInRoot() })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavPill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Row(Modifier.padding(Spacing.Xs), horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) { content() }
    }
}

@Composable
private fun NavDestination(item: NavItem, selected: Boolean, active: Boolean, dragging: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val lift by animateFloatAsState(if (dragging && active) 1.12f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "navLift")
    val container = animateTone(if (active) scheme.secondaryContainer else scheme.surfaceContainerHigh, label = "navContainer")
    val content = animateTone(if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant, label = "navContent")
    Box(
        modifier
            .heightIn(min = Spacing.MinTarget + Spacing.Sm)
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .clip(CircleShape)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Surface(Modifier.matchParentSize(), shape = CircleShape, color = container) {}
        Column(Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.Xs), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (active) item.selectedIcon else item.icon, contentDescription = null, tint = content)
            Text(item.label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
