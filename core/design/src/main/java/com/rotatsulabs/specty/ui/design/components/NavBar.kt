package com.rotatsulabs.specty.ui.design.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.rotatsulabs.specty.ui.design.Spacing
import com.rotatsulabs.specty.ui.design.animateTone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/** When the bar's destinations show their names. */
enum class NavLabels { Always, ActiveOnly, Never }

/** One destination of the bottom bar. [icon] is shown when it is not selected and [selectedIcon] when it is. */
class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

// Long S-curves with no initial speed spike, so the highlight leaves from rest the way liquid does.
private val LiquidEasing = CubicBezierEasing(0.32f, 0f, 0.14f, 1f)
private val SettleEasing = CubicBezierEasing(0.26f, 0f, 0.12f, 1f)
private val LabelEasing = CubicBezierEasing(0.16f, 0f, 0.12f, 1f)

/** How far past the halfway point the finger must go before another destination takes over, which stops flicker. */
private const val HoverHysteresis = 0.58f
private const val HoldPickupMs = 145L
private const val DragFollowRate = 16f
private const val WallFollowRate = 14f
private val MaxWallPull = 85.dp
private val PillHeight = 64.dp

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The bottom bar: floating pills with icons. Only the selected destination shows its label. The first items sit
 * together in the main pill and the last [separated] items get a pill of their own; [selected] counts across both.
 *
 * Hold the highlight, or slide from anywhere in the main pill, and it follows the finger across the bar. It eases
 * between destinations instead of jumping, stretches the bar at the ends, and settles into the one it is over when
 * released. The search button is tap only.
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
    labels: NavLabels = NavLabels.ActiveOnly,
    /** True when the bar sits inside another screen, where it takes no room for the keyboard or the system bars. */
    embedded: Boolean = false,
) {
    val split = items.size - separated
    val reduced = com.rotatsulabs.specty.ui.theme.LocalReducedMotion.current
    val rects = remember { mutableStateMapOf<Int, Rect>() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)

    // The highlight sits at a fractional position between destinations: 1.4 is 40% of the way from item 1 to item 2.
    val slot = remember { Animatable(selected.toFloat()) }
    val wall = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var hover by remember { mutableIntStateOf(selected) }
    var targetSlot by remember { mutableFloatStateOf(selected.toFloat()) }
    var targetWall by remember { mutableFloatStateOf(0f) }
    var settleTo by remember { mutableStateOf<Int?>(null) }
    val shown = if (dragging) hover else selected
    // Held, the highlight is picked up: it swells past the bar's rim and the icons under it grow with it.
    val magnify = remember { Animatable(0f) }
    // The name stays on the destination the highlight left until the highlight has arrived at the new one.
    var labelOwner by remember { mutableIntStateOf(selected) }
    LaunchedEffect(selected) {
        if (!dragging && labelOwner != selected) {
            androidx.compose.runtime.snapshotFlow { abs(slot.value - selected) }.first { it <= 0.35f }
            labelOwner = selected
        }
    }
    LaunchedEffect(dragging, reduced) {
        if (dragging && !reduced) {
            magnify.animateTo(1f, tween(420, easing = LiquidEasing))
        } else if (reduced) {
            magnify.snapTo(0f)
        } else {
            // Let go and the pill drops back and pinches in below its resting size before it rebounds, like a droplet settling.
            if (magnify.value > 0.05f) magnify.animateTo(0f, spring(dampingRatio = 0.28f, stiffness = 170f))
        }
    }
    // 0 while the bar shows destinations, 1 once search has filled the row.
    val open by animateFloatAsState(if (searching) 1f else 0f, spring(dampingRatio = 0.82f, stiffness = 380f), label = "navSearch")

    LaunchedEffect(selected) {
        if (!dragging && settleTo == null && slot.targetValue != selected.toFloat()) {
            slot.animateTo(selected.toFloat(), tween(400, easing = LiquidEasing))
        }
    }
    LaunchedEffect(dragging) {
        if (dragging) {
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                slot.snapTo(slot.value + (targetSlot - slot.value) * (1f - exp(-DragFollowRate * dt)))
                wall.snapTo(wall.value + (targetWall - wall.value) * (1f - exp(-WallFollowRate * dt)))
            }
        } else {
            launch { wall.animateTo(0f, tween(480, easing = SettleEasing)) }
            settleTo?.let { target ->
                slot.animateTo(target.toFloat(), spring(dampingRatio = 0.7f, stiffness = 300f))
                settleTo = null
            }
        }
    }

    val indicatorColor = MaterialTheme.colorScheme.secondaryContainer
    val onIndicator = MaterialTheme.colorScheme.onSecondaryContainer
    val geo = remember { FloatArray(4) }
    // Draws the highlight in a pill's own coordinates. Every pill draws it and clips to itself, so it slips from one to the next.
    val drawIndicator: DrawScope.(Offset) -> Unit = { pillRoot ->
        val n = items.size
        val s = slot.value.coerceIn(0f, (n - 1).toFloat())
        val i0 = floor(s).toInt().coerceIn(0, n - 1)
        val i1 = (i0 + 1).coerceAtMost(n - 1)
        val r0 = rects[i0]
        val r1 = rects[i1]
        if (r0 != null && r1 != null && open < 0.5f) {
            val f = s - i0
            val eased = smoothstep(f)
            val mid = sin(f * PI.toFloat())
            val raw = magnify.value
            val mag = raw.coerceIn(0f, 1f)
            // The spring may overshoot below rest: the pill briefly tightens, then eases out to its size.
            val dip = raw.coerceIn(-0.5f, 0f)
            // The pill takes the size of the destination it is over and blends between two while crossing, like a droplet.
            val h = lerp(r0.height, r1.height, eased) - mid * 2.dp.toPx() + mag * 6.dp.toPx() + dip * 18.dp.toPx()
            val w = lerp(r0.width, r1.width, eased) + mid * 8.dp.toPx() + mag * 6.dp.toPx() + dip * 26.dp.toPx()
            val cx = lerp(r0.center.x, r1.center.x, f) - pillRoot.x + wall.value * 4.dp.toPx()
            val cy = lerp(r0.center.y, r1.center.y, eased) - pillRoot.y
            // Whichever pill the centre is over draws it, held flush inside that pill.
            val outside = if (cx < 0f) -cx else if (cx > size.width) cx - size.width else 0f
            if (outside <= Spacing.Sm.toPx() / 2f + 1f) {
                val inset = lerp(3.dp.toPx(), (-0.5f).dp.toPx(), mag)
                val left = (cx - w / 2f).coerceIn(inset, (size.width - w - inset).coerceAtLeast(inset))
                val fade = (1f - 2f * open).coerceIn(0f, 1f)
                val radius = CornerRadius(minOf(w, h) / 2f)
                if (mag > 0.005f) {
                    val pad = 2.dp.toPx() * mag
                    drawRoundRect(Color.Black.copy(alpha = 0.09f * mag * fade), Offset(left - pad, cy - h / 2f - pad), Size(w + pad * 2f, h + pad * 2f), CornerRadius(minOf(w, h) / 2f + pad))
                }
                drawRoundRect(indicatorColor.copy(alpha = indicatorColor.alpha * fade), Offset(left, cy - h / 2f), Size(w, h), radius)
                if (mag > 0.005f) {
                    drawRoundRect(onIndicator.copy(alpha = 0.14f * mag * fade), Offset(left, cy - h / 2f), Size(w, h), radius, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                }
            }
        }
    }

    // The keyboard lifts only this bar, in step with its own animation; the screen behind stays where it is.
    val ime = WindowInsets.ime
    val navInset = WindowInsets.navigationBars
    Row(
        modifier
            .then(if (embedded) Modifier else Modifier.offset { IntOffset(0, -(ime.getBottom(this) - navInset.getBottom(this)).coerceAtLeast(0)) })
            .fillMaxWidth()
            .then(if (embedded) Modifier else Modifier.navigationBarsPadding().padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm))
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(split, searching, reduced) {
                if (searching) return@pointerInput
                // Only the destinations in the main pill can be dragged between; search opens by a tap.
                val n = split
                val slop = viewConfiguration.touchSlop
                val nearPill = 36.dp.toPx()
                val maxWall = MaxWallPull.toPx()
                val out = FloatArray(2)

                fun centers(): FloatArray? {
                    val c = FloatArray(n)
                    for (i in 0 until n) c[i] = (rects[i] ?: return null).center.x
                    return c
                }
                fun nearest(x: Float): Int = rects.entries.minByOrNull { abs(it.value.center.x - x) }?.key ?: currentSelected
                // Maps where the highlight wants to be onto a fractional slot, plus how hard it is pushing past either end.
                fun project(desired: Float): Boolean {
                    val c = centers() ?: return false
                    val dir = if (c[n - 1] >= c[0]) 1f else -1f
                    val d = desired * dir
                    val lo = c[0] * dir
                    val hi = c[n - 1] * dir
                    val raw = if (d < lo) d - lo else if (d > hi) d - hi else 0f
                    out[1] = if (raw == 0f) 0f else sign(raw) * tanh(abs(raw) / maxWall) * dir
                    val x = d.coerceIn(minOf(lo, hi), maxOf(lo, hi))
                    var s = (n - 1).toFloat()
                    for (i in 0 until n - 1) {
                        val a = c[i] * dir
                        val b = c[i + 1] * dir
                        if (x <= b) { s = i + ((x - a) / (b - a).coerceAtLeast(1f)).coerceIn(0f, 1f); break }
                    }
                    out[0] = s
                    return true
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (n < 2) return@awaitEachGesture
                    val downX = down.position.x + origin.x
                    val downIndex = nearest(downX)
                    if (downIndex >= n) return@awaitEachGesture
                    val startSelected = currentSelected
                    val pillCenter = centers()?.let { c ->
                        val s = slot.value.coerceIn(0f, (n - 1).toFloat())
                        val i = floor(s).toInt().coerceIn(0, n - 1)
                        lerp(c[i], c[(i + 1).coerceAtMost(n - 1)], s - i)
                    } ?: downX
                    // Holding the active destination or the highlight itself picks it up without sliding first.
                    val canHold = downIndex == startSelected || abs(downX - pillCenter) <= nearPill
                    val deadline = SystemClock.uptimeMillis() + HoldPickupMs
                    var lastX = downX
                    var slopOffset = 0f
                    var started = false
                    while (!started) {
                        val remaining = if (canHold) deadline - SystemClock.uptimeMillis() else Long.MAX_VALUE
                        if (canHold && remaining <= 0L) { started = true; break }
                        val event = (
                            if (canHold) {
                                withTimeoutOrNull(remaining.coerceAtLeast(1L)) { awaitPointerEvent(PointerEventPass.Initial) }
                            } else {
                                awaitPointerEvent(PointerEventPass.Initial)
                            }
                            ) ?: continue
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        lastX = change.position.x + origin.x
                        val dx = lastX - downX
                        val dy = change.position.y - down.position.y
                        if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
                        if (abs(dx) > slop) {
                            slopOffset = sign(dx) * slop
                            change.consume()
                            started = true
                        }
                    }

                    val dragOrigin = if (slopOffset == 0f) lastX else downX
                    val anchor = if (abs(downX - pillCenter) <= nearPill) pillCenter else rects[downIndex]?.center?.x ?: downX
                    if (project(anchor + (lastX - dragOrigin) - slopOffset)) {
                        targetSlot = out[0]; targetWall = if (reduced) 0f else out[1]
                    } else {
                        targetSlot = downIndex.toFloat(); targetWall = 0f
                    }
                    hover = downIndex
                    settleTo = null
                    dragging = true
                    if (downIndex != startSelected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            lastX = change.position.x + origin.x
                            if (project(anchor + (lastX - dragOrigin) - slopOffset)) {
                                targetSlot = out[0]; targetWall = if (reduced) 0f else out[1]
                                val s = out[0].coerceIn(0f, (n - 1).toFloat())
                                val next = if (abs(s - hover) <= HoverHysteresis) hover else s.roundToInt().coerceIn(0, n - 1)
                                if (next != hover) {
                                    hover = next
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                            if (!change.pressed) { released = true; break }
                        }
                    } finally {
                        val target = if (released) hover.coerceIn(0, n - 1) else currentSelected
                        targetWall = 0f
                        settleTo = target
                        labelOwner = target
                        dragging = false
                        if (target != currentSelected) current(target)
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One layout for both states: the search pill is a circle at rest and stretches across the row as it opens,
        // while the main pill shrinks into a single button. Nothing is swapped; the same two pills move.
        MorphRow(open, Modifier.fillMaxWidth()) {
            NavPill(
                Modifier.graphicsLayer {
                    // Pulled past an end, the whole bar, icons and all, stretches that way from the opposite edge.
                    val pull = wall.value
                    if (size.width > 0f && pull != 0f && open < 0.01f) {
                        val mag = abs(pull)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (pull < 0f) 1f else 0f, 0.5f)
                        scaleX = 1f + mag * 22.dp.toPx() / size.width
                        scaleY = 1f - mag * 0.045f
                        translationX = pull * 6.dp.toPx()
                    }
                },
                indicator = drawIndicator, stretch = { wall.value },
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.fillMaxWidth().graphicsLayer { alpha = (1f - 2f * open).coerceIn(0f, 1f) },
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (i in 0 until split) {
                            NavDestination(
                                items[i], i == selected, i == shown, { current(i) },
                                showLabel = when (labels) { NavLabels.Always -> true; NavLabels.Never -> false; NavLabels.ActiveOnly -> i == labelOwner },
                                coverage = { smoothstep(1f - (abs(slot.value - i) / 0.65f).coerceIn(0f, 1f)) },
                                magnify = { magnify.value },
                                modifier = (if (labels == NavLabels.Always) Modifier.weight(1f) else Modifier)
                                    .onGloballyPositioned { if (abs(wall.value) < 0.002f) rects[i] = it.boundsInRoot() },
                                compact = labels == NavLabels.Always,
                            )
                        }
                    }
                    if (open > 0.02f) {
                        Box(Modifier.graphicsLayer { alpha = (2f * open - 1f).coerceIn(0f, 1f) }) {
                            NavDestination(items[backTo], selected = false, active = false, onClick = { current(backTo) }, showLabel = false, coverage = { 0f }, magnify = { 0f })
                        }
                    }
                }
            }
            NavPill(
                Modifier.graphicsLayer {
                    // The search button is carried along as the bar stretches toward it.
                    val pull = wall.value
                    translationX = if (open > 0.01f) 0f else if (pull > 0f) pull * (22.dp.toPx() + 6.dp.toPx()) else pull * 6.dp.toPx() * 0.35f
                },
                indicator = drawIndicator, stretch = { wall.value },
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.graphicsLayer { alpha = (1f - 2f * open).coerceIn(0f, 1f) }.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        for (i in split until items.size) {
                            NavDestination(
                                items[i], i == selected, i == shown, { current(i) }, showLabel = false,
                                coverage = { smoothstep(1f - (abs(slot.value - i) / 0.65f).coerceIn(0f, 1f)) }, magnify = { magnify.value },
                                modifier = Modifier.onGloballyPositioned { if (abs(wall.value) < 0.002f) rects[i] = it.boundsInRoot() },
                            )
                        }
                    }
                    if (open > 0.02f) {
                        SearchField(
                            query, onQuery,
                            Modifier.fillMaxWidth().graphicsLayer { alpha = (2f * open - 1f).coerceIn(0f, 1f) },
                            placeholder = placeholder, focusRequester = focusRequester,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bar's two pills side by side, centred: the main pill takes the width its destinations need and the second is a
 * circle at progress 0. At 1 the second fills the row and the first has become a circle.
 */
@Composable
private fun MorphRow(progress: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val h = PillHeight.roundToPx()
        val gap = Spacing.Sm.roundToPx()
        val w = constraints.maxWidth
        val p = progress.coerceIn(0f, 1f)
        val natural = measurables[0].maxIntrinsicWidth(h).coerceAtMost(w - h - gap)
        val mainW = lerp(natural.toFloat(), h.toFloat(), p).roundToInt()
        val searchW = lerp(h.toFloat(), (w - h - gap).toFloat(), p).roundToInt()
        val total = mainW + gap + searchW
        val x0 = (((w - total) / 2f) * (1f - p)).roundToInt()
        val a = measurables[0].measure(Constraints.fixed(mainW.coerceAtLeast(0), h))
        val b = measurables[1].measure(Constraints.fixed(searchW.coerceAtLeast(0), h))
        layout(w, h) {
            a.place(x0, 0)
            b.place(x0 + mainW + gap, 0)
        }
    }
}

/** A floating pill with its own shadow, and the selection highlight drawn inside it. */
@Composable
private fun NavPill(
    modifier: Modifier = Modifier,
    indicator: (DrawScope.(Offset) -> Unit)? = null,
    /** How far the pill is stretched, so its resting position is kept while it is. */
    stretch: () -> Float = { 0f },
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    var root by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .shadow(3.dp, CircleShape, clip = false)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .onGloballyPositioned { if (abs(stretch()) < 0.002f) root = it.positionInRoot() }
            .drawBehind { if (indicator != null) indicator(root) }
            .padding(Spacing.Xs),
        content = content,
    )
}

/** An icon that also shows its label while it is the selected destination, the label growing out beside it. */
@Composable
private fun NavDestination(
    item: NavItem,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    showLabel: Boolean,
    coverage: () -> Float,
    magnify: () -> Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val label by animateFloatAsState(if (showLabel) 1f else 0f, tween(330, easing = LabelEasing), label = "navLabel")
    // The icon takes the highlight's colour as the highlight passes under it, and swells with it while it is held.
    val content = androidx.compose.ui.graphics.lerp(scheme.onSurfaceVariant, scheme.onSecondaryContainer, coverage())
    val gap = with(LocalDensity.current) { (if (compact) 4.dp else 8.dp).roundToPx() }
    Row(
        modifier
            .height(Spacing.MinTarget + Spacing.Sm)
            .clip(CircleShape)
            // The moving highlight is the feedback; a ripple would flash a second, lighter one over it.
            .selectable(selected = selected, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = onClick, role = Role.Tab)
            .graphicsLayer {
                val s = 1f + 0.13f * magnify() * coverage()
                scaleX = s
                scaleY = s
            }
            .padding(horizontal = if (compact) 8.dp else 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (active) item.selectedIcon else item.icon, contentDescription = null, tint = content, modifier = Modifier.requiredSize(24.dp))
        Text(
            item.label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .alpha(label)
                .clipToBounds()
                .layout { measurable, _ ->
                    val p = measurable.measure(Constraints())
                    val w = ((p.width + gap) * label).roundToInt()
                    layout(w, p.height) { p.place((gap * label).roundToInt(), 0) }
                },
        )
    }
}
