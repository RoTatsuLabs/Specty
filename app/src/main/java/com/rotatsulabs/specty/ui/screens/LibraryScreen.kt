package com.rotatsulabs.specty.ui.screens

import com.rotatsulabs.specty.ui.components.withoutExplicitMark
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.EmptyState
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.Pill
import com.rotatsulabs.specty.ui.design.components.RingProgress
import com.rotatsulabs.specty.ui.design.components.SearchField
import com.rotatsulabs.specty.ui.design.components.Segment
import com.rotatsulabs.specty.ui.design.components.SegmentedControl
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import com.rotatsulabs.specty.ui.design.components.SpectyCard
import com.rotatsulabs.specty.ui.design.components.VerdictPill
import com.rotatsulabs.specty.ui.design.components.WavyProgressBar
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radar
import com.rotatsulabs.specty.batch.BatchScanner
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.data.Track
import com.rotatsulabs.specty.ui.LibraryState
import com.rotatsulabs.specty.ui.components.CoverImage
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.components.pressScale
import com.rotatsulabs.specty.ui.design.CoverFlight
import com.rotatsulabs.specty.ui.design.CoverSource
import com.rotatsulabs.specty.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private enum class SortKey(val label: String) {
    Title("Title"), Artist("Artist"), Duration("Length"), Size("Size"),
}

private enum class Filter(val label: String) {
    All("All"), Unchecked("Unchecked"), Genuine("Genuine"), Suspect("Suspect"),
}

private fun Verdict.isSuspect() =
    this == Verdict.LOSSY_SOURCE || this == Verdict.UPSAMPLED || this == Verdict.POSSIBLE_LOSSY_SOURCE

/**
 * The main screen. A single scrolling page: brand header, a library health ring, search, filters and either a
 * list or a cover grid of tracks. Analysing a file is a floating action rather than a navigation tab.
 */
@Composable
fun LibraryScreen(
    state: LibraryState,
    onLoad: (force: Boolean) -> Unit,
    onOpen: (Track, androidx.compose.ui.geometry.Rect?, CoverSource?) -> Unit,
    onPickFile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    verdicts: Map<String, Verdict>,
    keyFor: (Track) -> String,
    batch: BatchScanner.State = BatchScanner.State(),
    onScanAll: () -> Unit = {},
    onCancelScan: () -> Unit = {},
    onDismissScan: () -> Unit = {},
    dockVisible: Boolean = false,
    flight: CoverFlight? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok }
    LaunchedEffect(granted) { if (granted) onLoad(false) }
    // Notifications are optional: the scan runs either way, the permission only shows its progress.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onScanAll() }
    val startScan = {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) onScanAll() else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var query by rememberSaveable { mutableStateOf("") }
    var sortIndex by rememberSaveable { mutableStateOf(0) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    var grid by rememberSaveable { mutableStateOf(false) }
    val sort = SortKey.entries[sortIndex]
    val filter = Filter.entries[filterIndex]

    val verdictByTrack = remember(state.tracks, verdicts, keyFor) {
        state.tracks.associate { it.uri.toString() to verdicts[keyFor(it)] }
    }
    val checked = verdictByTrack.values.count { it != null }
    val suspects = verdictByTrack.values.count { it != null && it.isSuspect() }
    val genuine = verdictByTrack.values.count { it == Verdict.GENUINE }

    val visible = remember(state.tracks, verdictByTrack, query, sort, ascending, filter) {
        val searched = if (query.isBlank()) {
            state.tracks
        } else {
            state.tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                    (it.artist?.contains(query, ignoreCase = true) == true) ||
                    (it.album?.contains(query, ignoreCase = true) == true)
            }
        }
        val filtered = searched.filter {
            val v = verdictByTrack[it.uri.toString()]
            when (filter) {
                Filter.All -> true
                Filter.Unchecked -> v == null
                Filter.Genuine -> v == Verdict.GENUINE
                Filter.Suspect -> v != null && v.isSuspect()
            }
        }
        val sorted = when (sort) {
            SortKey.Title -> filtered.sortedBy { it.title.lowercase() }
            SortKey.Artist -> filtered.sortedBy { (it.artist ?: "￿").lowercase() }
            SortKey.Duration -> filtered.sortedBy { it.durationMs }
            SortKey.Size -> filtered.sortedBy { it.sizeBytes }
        }
        if (ascending) sorted else sorted.reversed()
    }
    val hasTracks = granted && !state.loading && state.error == null && state.tracks.isNotEmpty()

    val listState = rememberLazyListState()
    var fabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var previous = 0
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }.collect { now ->
            if (now > previous + 12) fabVisible = false else if (now < previous - 12) fabVisible = true
            previous = now
        }
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = WindowInsets.statusBars.asPaddingValues(),
        ) {
            item(key = "header") {
                val titleFx = Modifier.graphicsLayer {
                    val offset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 1200f
                    alpha = (1f - offset / 260f).coerceIn(0f, 1f)
                    translationY = offset * 0.45f
                    val s = (1f - offset / 1600f).coerceIn(0.8f, 1f)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                Header(
                    onRescan = { onLoad(true) },
                    onSettings = onOpenSettings,
                    onAbout = onOpenAbout,
                    canRescan = granted,
                    canScan = granted && state.tracks.isNotEmpty() && !batch.running,
                    onScanAll = startScan,
                    brandModifier = titleFx,
                )
            }

            if (batch.running || batch.finished) {
                item(key = "batch") {
                    BatchCard(batch, onCancel = onCancelScan, onDismiss = onDismissScan)
                }
            }
            item(key = "search") {
                Spacer(Modifier.height(14.dp))
                SearchPill(query) { query = it }
            }
            if (hasTracks) {
                item(key = "filters") {
                    FilterRow(
                        selected = filter,
                        counts = mapOf(
                            Filter.All to state.tracks.size,
                            Filter.Unchecked to state.tracks.size - checked,
                            Filter.Genuine to genuine,
                            Filter.Suspect to suspects,
                        ),
                        onSelect = { filterIndex = it.ordinal },
                    )
                }
                item(key = "section") {
                    SectionBar(
                        count = visible.size,
                        sort = sort,
                        ascending = ascending,
                        grid = grid,
                        onSort = { sortIndex = it.ordinal },
                        onToggleOrder = { ascending = !ascending },
                        onToggleGrid = { grid = !grid },
                    )
                }
            }

            when {
                !granted -> item(key = "permission") {
                    Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth()) {
                        PermissionPrompt { launcher.launch(Manifest.permission.READ_MEDIA_AUDIO) }
                    }
                }
                state.loading && state.tracks.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ShapeLoader(size = 56.dp)
                    }
                }
                state.error != null -> item(key = "error") {
                    Box(Modifier.fillParentMaxHeight(0.5f).fillMaxWidth()) { Message(state.error) }
                }
                state.tracks.isEmpty() -> item(key = "empty") {
                    Box(Modifier.fillParentMaxHeight(0.5f).fillMaxWidth()) {
                        Message("No audio files found on this device. Use the button below to pick one.")
                    }
                }
                visible.isEmpty() -> item(key = "nomatch") {
                    Box(Modifier.fillParentMaxHeight(0.4f).fillMaxWidth()) { Message("Nothing here yet for this filter.") }
                }
                grid -> itemsIndexed(visible.chunked(2), key = { _, pair -> "g" + pair.first().uri.toString() }) { rowIndex, pair ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).animateItem(placementSpec = Motion.spatial()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        pair.forEachIndexed { i, track ->
                            TrackTile(track, rowIndex * 2 + i, verdictByTrack[track.uri.toString()], { b, c -> onOpen(track, b, c) }, Modifier.weight(1f), flight)
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                else -> itemsIndexed(visible, key = { _, t -> t.uri.toString() }) { index, track ->
                    TrackRow(
                        track, index, verdictByTrack[track.uri.toString()], { b, c -> onOpen(track, b, c) },
                        Modifier.padding(horizontal = 10.dp).animateItem(placementSpec = Motion.spatial()),
                        flight,
                    )
                }
            }
            item(key = "bottom") {
                Spacer(Modifier.navigationBarsPadding().height(if (dockVisible) 130.dp else 110.dp))
            }
        }

        AnimatedVisibility(
            visible = fabVisible && !dockVisible,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = scaleIn(Motion.spatial(), initialScale = 0.6f) + fadeIn(Motion.effects()),
            exit = scaleOut(Motion.spatial(), targetScale = 0.6f) + fadeOut(Motion.effects()),
        ) {
            val haptic = LocalHapticFeedback.current
            MorphSurface(
                Modifier.navigationBarsPadding().padding(20.dp),
                corners = SpectyShapes.Feature,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPickFile()
                },
                role = androidx.compose.ui.semantics.Role.Button,
            ) {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                    Text("Analyse a file", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

// ---- header --------------------------------------------------------------------------------

@Composable
private fun Header(onRescan: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit, canRescan: Boolean, canScan: Boolean, onScanAll: () -> Unit, brandModifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    val menuTurn by animateFloatAsState(if (menu) 90f else 0f, Motion.spatial(), label = "menuTurn")
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).then(brandModifier), verticalAlignment = Alignment.CenterVertically) {
            EqualizerMark()
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Specty", style = MaterialTheme.typography.headlineLarge)
                Text("Audio quality lab", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box {
            ExpressiveIconButton(Icons.Rounded.MoreVert, "More options", { menu = true }, toggled = menu, iconModifier = Modifier.rotate(menuTurn))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                if (canRescan) {
                    DropdownMenuItem(
                        text = { Text("Rescan library") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        onClick = { menu = false; onRescan() },
                    )
                }
                if (canScan) {
                    DropdownMenuItem(
                        text = { Text("Scan whole library") },
                        leadingIcon = { Icon(Icons.Rounded.Radar, contentDescription = null) },
                        onClick = { menu = false; onScanAll() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = { menu = false; onSettings() },
                )
                DropdownMenuItem(
                    text = { Text("About") },
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    onClick = { menu = false; onAbout() },
                )
            }
        }
    }
}

/** The app mark: four bars that keep dancing out of phase. */
@Composable
private fun EqualizerMark() {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val container = MaterialTheme.colorScheme.primaryContainer
    val bars = MaterialTheme.colorScheme.onPrimaryContainer
    MorphSurface(Modifier.size(52.dp), corners = SpectyShapes.Segment, color = container, morphOnPress = false) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val count = 4
            val gap = size.width * 0.12f
            val barWidth = (size.width - gap * (count - 1)) / count
            for (i in 0 until count) {
                val h = 0.28f + 0.72f * abs(sin(phase.value * 2f * PI.toFloat() + i * 0.9f))
                val barHeight = size.height * h
                drawRoundRect(
                    color = bars,
                    topLeft = Offset(i * (barWidth + gap), (size.height - barHeight) / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f),
                )
            }
        }
    }
}

// ---- batch scan ----------------------------------------------------------------------------------

@Composable
private fun BatchCard(batch: BatchScanner.State, onCancel: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SpectyCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        corners = SpectyShapes.Card,
        color = scheme.secondaryContainer,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSecondaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            batch.running -> "Scanning library"
                            batch.cancelled -> "Scan cancelled"
                            else -> "Scan finished"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (batch.running && batch.current.isNotEmpty()) batch.current else
                            "${batch.done} analysed" + (if (batch.skipped > 0) ", ${batch.skipped} already saved" else "") +
                                (if (batch.flagged > 0) ", ${batch.flagged} need a look" else "") +
                                (if (batch.failed > 0) ", ${batch.failed} failed" else ""),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (batch.running) {
                    ExpressiveButton("Cancel", onCancel, kind = ButtonKind.Text)
                } else {
                    ExpressiveIconButton(Icons.Rounded.Close, "Dismiss", onDismiss, tonal = false)
                }
            }
            if (batch.running) {
                Spacer(Modifier.height(10.dp))
                WavyProgressBar(batch.fraction, color = scheme.onSecondaryContainer, track = scheme.onSecondaryContainer.copy(alpha = 0.18f))
                Spacer(Modifier.height(6.dp))
                Text("${batch.processed} of ${batch.total}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ---- library health ---------------------------------------------------------------------------

// ---- search, filters, section bar ---------------------------------------------------------------

@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit) {
    SearchField(query, onChange, Modifier.padding(horizontal = 16.dp), placeholder = "Search your library")
}

@Composable
private fun FilterRow(selected: Filter, counts: Map<Filter, Int>, onSelect: (Filter) -> Unit) {
    val haptic = LocalHapticFeedback.current
    SegmentedControl(
        segments = Filter.entries.map { Segment(it, it.label, badge = "${counts[it] ?: 0}") },
        selected = selected,
        onSelect = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(it)
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SectionBar(
    count: Int,
    sort: SortKey,
    ascending: Boolean,
    grid: Boolean,
    onSort: (SortKey) -> Unit,
    onToggleOrder: () -> Unit,
    onToggleGrid: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val rotation by animateFloatAsState(if (ascending) 0f else 180f, Motion.spatial(), label = "sortArrow")
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = { (slideInVertically(Motion.spatial()) { it / 2 } + fadeIn()) togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "songCount",
        ) { n ->
            Text(
                if (n == 1) "1 track" else "$n tracks",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            MorphSurface(
                Modifier.height(40.dp),
                corners = SpectyShapes.Pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { menu = true },
            ) {
                Box(Modifier.height(40.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(sort.label, style = MaterialTheme.typography.labelLarge)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                SortKey.entries.forEach { key ->
                    DropdownMenuItem(
                        text = { Text(key.label, fontWeight = if (key == sort) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { menu = false; onSort(key) },
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        ExpressiveIconButton(
            Icons.Rounded.KeyboardArrowUp,
            if (ascending) "Ascending" else "Descending",
            { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggleOrder() },
            iconModifier = Modifier.rotate(rotation),
        )
        Spacer(Modifier.width(6.dp))
        ExpressiveIconButton(
            if (grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
            if (grid) "Show as list" else "Show as grid",
            { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggleGrid() },
            toggled = grid,
        )
    }
}

// ---- tracks -----------------------------------------------------------------------------------------

@Composable
private fun CoverFallback(track: Track) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

/** Flat row: no card, just cover, text and a trailing badge. Pressing morphs the corners. */
@Composable
private fun TrackRow(
    track: Track,
    index: Int,
    verdict: Verdict?,
    onOpen: (Rect?, CoverSource?) -> Unit,
    modifier: Modifier = Modifier,
    flight: CoverFlight? = null,
) {
    val appear = rememberAppear(index)
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var coverBounds by remember { mutableStateOf<Rect?>(null) }
    MorphSurface(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 48f
                val pop = 0.88f + 0.12f * appear.value
                scaleX = pop
                scaleY = pop
            },
        corners = SpectyShapes.Row,
        color = Color.Transparent,
        onClick = { onOpen(bounds, CoverSource(coverBounds, SpectyShapes.Cover, 180)) },
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(
                uri = track.uri,
                shape = SpectyShapes.Cover.toShape(),
                modifier = Modifier
                    .size(60.dp)
                    .onGloballyPositioned { coverBounds = it.boundsInRoot() }
                    .graphicsLayer { alpha = if (flight?.hides(track.uri) == true) 0f else 1f },
                sizePx = 180,
            ) { CoverFallback(track) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title.withoutExplicitMark(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val parts = listOfNotNull(track.artist, Fmt.duration(track.durationMs / 1000.0))
                Text(
                    parts.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (track.extension.isNotEmpty()) {
                    Text(track.extension, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (verdict != null) VerdictBadge(verdict)
            }
        }
    }
}

/** Cover-first tile for the grid view, with the format and verdict laid over the artwork. */
@Composable
private fun TrackTile(
    track: Track,
    index: Int,
    verdict: Verdict?,
    onOpen: (Rect?, CoverSource?) -> Unit,
    modifier: Modifier = Modifier,
    flight: CoverFlight? = null,
) {
    val appear = rememberAppear(index / 2)
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var coverBounds by remember { mutableStateOf<Rect?>(null) }
    MorphSurface(
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 64f
                val pop = 0.86f + 0.14f * appear.value
                scaleX = pop
                scaleY = pop
            },
        corners = SpectyShapes.Tile,
        color = Color.Transparent,
        onClick = { onOpen(bounds, CoverSource(coverBounds, SpectyShapes.Tile, 420)) },
    ) {
        Column(Modifier.padding(bottom = 6.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                CoverImage(
                    uri = track.uri,
                    shape = SpectyShapes.Tile.toShape(),
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coverBounds = it.boundsInRoot() }
                        .graphicsLayer { alpha = if (flight?.hides(track.uri) == true) 0f else 1f },
                    sizePx = 420,
                ) { CoverFallback(track) }
                if (verdict != null) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { VerdictBadge(verdict) }
                if (track.extension.isNotEmpty()) {
                    Pill(
                        track.extension,
                        Modifier.align(Alignment.BottomStart).padding(10.dp),
                        container = Color.Black.copy(alpha = 0.55f),
                        content = Color.White,
                    )
                }
            }
            Text(
                track.title.withoutExplicitMark(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp),
            )
            Text(
                track.artist ?: Fmt.duration(track.durationMs / 1000.0),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

/** Staggered entrance progress (0..1) for the first screenful of items. */
@Composable
private fun rememberAppear(index: Int): androidx.compose.runtime.State<Float> {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (index < 12) delay(index * 35L)
        shown = true
    }
    return animateFloatAsState(if (shown) 1f else 0f, Motion.spatial(), label = "appear")
}

@Composable
private fun VerdictBadge(verdict: Verdict) = VerdictPill(verdict)

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    EmptyState(
        icon = Icons.Rounded.MusicNote,
        title = "Allow access to your music",
        message = "Specty reads your audio files on this device to list them here. Nothing leaves your phone.",
        modifier = Modifier.fillMaxSize(),
        action = { ExpressiveButton("Grant access", onGrant) },
    )
}

@Composable
private fun Message(text: String) {
    EmptyState(icon = Icons.Rounded.MusicNote, title = "Nothing to show", message = text, modifier = Modifier.fillMaxSize())
}

