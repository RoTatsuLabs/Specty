package com.rotatsulabs.specty.ui.screens

import com.rotatsulabs.specty.ui.components.withoutExplicitMark
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ExpressiveMenuItem
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.EmptyState
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.Pill
import com.rotatsulabs.specty.ui.design.components.RingProgress
import com.rotatsulabs.specty.ui.design.components.SearchField
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import com.rotatsulabs.specty.ui.design.components.OptionSegmentedControl
import com.rotatsulabs.specty.ui.design.components.SpectyCard
import com.rotatsulabs.specty.ui.design.components.VerdictPill
import com.rotatsulabs.specty.ui.design.components.WavyProgressBar
import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import com.rotatsulabs.specty.ui.design.AdaptiveMath
import com.rotatsulabs.specty.ui.design.rememberWidthDp
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radar
import com.rotatsulabs.specty.batch.BatchScanner
import com.rotatsulabs.specty.batch.ScanNote
import com.rotatsulabs.specty.batch.res
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.data.LibraryFolder
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

@StringRes
internal fun SortField.labelRes(): Int = when (this) {
    SortField.Title -> R.string.sort_title
    SortField.Artist -> R.string.sort_artist
    SortField.Album -> R.string.sort_album
    SortField.Duration -> R.string.sort_length
    SortField.Size -> R.string.sort_size
    SortField.Modified -> R.string.sort_modified
    SortField.Result -> R.string.sort_result
}

@StringRes
private fun BrowseKind.labelRes(): Int = when (this) {
    BrowseKind.Tracks -> R.string.browse_tracks
    BrowseKind.Albums -> R.string.browse_albums
    BrowseKind.Artists -> R.string.browse_artists
}

private fun GroupField.labelRes(): Int = when (this) {
    GroupField.None -> R.string.group_none
    GroupField.Result -> R.string.group_result
    GroupField.Format -> R.string.group_format
}

/** Results from the ones worth a look down to tracks not checked yet; this is the order of the Result sort and groups. */
private val RESULT_ORDER = ResultFilter.ORDER

@Composable
private fun groupTitle(by: GroupField, group: TrackGroup<Track>): String = when (by) {
    GroupField.Result -> stringResource(RESULT_ORDER[group.rank].labelRes)
    GroupField.Format -> group.key ?: stringResource(R.string.group_unknown_format)
    GroupField.None -> ""
}

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
    onPickFolder: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenInsights: () -> Unit = {},
    onPlayAll: (shuffle: Boolean) -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenCollection: (String) -> Unit = {},
    verdicts: Map<String, Verdict>,
    keyFor: (Track) -> String,
    batch: BatchScanner.State = BatchScanner.State(),
    onScanAll: () -> Unit = {},
    onCancelScan: () -> Unit = {},
    onDismissScan: () -> Unit = {},
    onCompare: (Track, Track) -> Unit = { _, _ -> },
    dockVisible: Boolean = false,
    flight: CoverFlight? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val hasFolders = state.folders.isNotEmpty()
    LaunchedEffect(Unit) { onLoad(false) }
    // Notifications are optional: the scan runs either way, the permission only shows its progress.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onScanAll() }
    val startScan = {
        // The permission only exists from Android 13.
        val allowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (allowed) onScanAll() else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var query by rememberSaveable { mutableStateOf("") }
    // The layout is remembered between launches; the filter and the picks only for this visit.
    val prefs = remember { context.getSharedPreferences("specty", Context.MODE_PRIVATE) }
    var layout by remember { mutableStateOf(LibraryView.load(prefs)) }
    val setLayout = { next: LibraryView -> layout = next; next.save(prefs) }
    var filterMask by rememberSaveable { mutableStateOf(0) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var pickedRaw by rememberSaveable { mutableStateOf("") }
    val filters = remember(filterMask) { ResultFilter.fromMask(filterMask) }

    val verdictByTrack = remember(state.tracks, verdicts, keyFor) {
        state.tracks.associate { it.uri.toString() to verdicts[keyFor(it)] }
    }

    val filterCounts = remember(verdictByTrack) { ResultFilter.counts(verdictByTrack.values) }

    val unscanned = remember(verdictByTrack) { verdictByTrack.values.count { it == null } }

    val fields = remember(verdictByTrack) {
        TrackFields<Track>(
            title = { it.title }, artist = { it.artist }, album = { it.album }, durationMs = { it.durationMs },
            sizeBytes = { it.sizeBytes }, modifiedSec = { it.modifiedSec }, format = { it.extension },
            resultRank = { RESULT_ORDER.indexOf(ResultFilter.of(verdictByTrack[it.uri.toString()])) },
        )
    }
    val groups = remember(state.tracks, fields, query, layout, filters) {
        val searched = if (query.isBlank()) {
            state.tracks
        } else {
            state.tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                    (it.artist?.contains(query, ignoreCase = true) == true) ||
                    (it.album?.contains(query, ignoreCase = true) == true)
            }
        }
        val filtered = searched.filter { filters.accepts(verdictByTrack[it.uri.toString()]) }
        LibraryOrder.group(LibraryOrder.sort(filtered, layout.sort, layout.ascending, fields), layout.group, fields)
    }
    val visible = remember(groups) { groups.flatMap { it.items } }
    val collections = remember(visible, layout.browse, layout.ascending, fields) {
        val list = when (layout.browse) {
            BrowseKind.Albums -> Browse.albums(visible, fields)
            BrowseKind.Artists -> Browse.artists(visible, fields)
            BrowseKind.Tracks -> emptyList()
        }
        if (layout.ascending) list else list.reversed()
    }

    val picks = remember(pickedRaw, state.tracks) {
        ComparePicks.decode(pickedRaw).mapNotNull { id -> state.tracks.firstOrNull { it.uri.toString() == id } }
    }
    val pickedIds = remember(picks) { picks.map { it.uri.toString() }.toSet() }
    val selecting = picks.isNotEmpty()
    BackHandler(enabled = selecting) { pickedRaw = "" }
    val pickHaptic = LocalHapticFeedback.current
    val togglePick = { track: Track ->
        pickHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
        pickedRaw = ComparePicks.encode(ComparePicks.toggle(ComparePicks.decode(pickedRaw), track.uri.toString()))
    }
    val hasTracks = hasFolders && state.error == null && state.tracks.isNotEmpty()

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
        if (showFilter && hasTracks) {
            FilterSheet(
                selected = filters,
                counts = filterCounts,
                shown = visible.size,
                onToggle = { f -> filterMask = ResultFilter.toMask(if (f in filters) filters - f else filters + f) },
                onClear = { filterMask = 0 },
                onDismiss = { showFilter = false },
            )
        }
        val widthDp = rememberWidthDp()
        val columns = AdaptiveMath.columns(widthDp)
        LazyColumn(
            Modifier.fillMaxSize().widthIn(max = (if (layout.grid) AdaptiveMath.LIBRARY_MAX else 760).dp).align(Alignment.TopCenter),
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
                    onFolders = onOpenFolders,
                    onInsights = onOpenInsights,
                    onPlayAll = onPlayAll,
                    canInsights = state.tracks.isNotEmpty(),
                    canRescan = hasFolders,
                    canScan = state.tracks.isNotEmpty() && !batch.running,
                    unscanned = unscanned,
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
                Box {
                    SearchPill(query, { query = it }) {
                        if (hasTracks) {
                            Spacer(Modifier.width(8.dp))
                            FilterButton(filters.size, { showFilter = true })
                        }
                    }
                }
            }
            if (hasTracks) {
                item(key = "browse") {
                    OptionSegmentedControl(
                        BrowseKind.entries, layout.browse, { setLayout(layout.copy(browse = it)) },
                        { stringResource(it.labelRes()) },
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            if (hasTracks) {
                item(key = "activeFilters") {
                    ActiveFilters(
                        selected = filters,
                        onRemove = { f -> filterMask = ResultFilter.toMask(filters - f) },
                        onClear = { filterMask = 0 },
                    )
                }
            }
            if (hasTracks) {
                item(key = "section") {
                    Box {
                    SectionBar(
                        count = if (layout.browse == BrowseKind.Tracks) visible.size else collections.size,
                        browse = layout.browse,
                        sort = layout.sort,
                        group = layout.group,
                        ascending = layout.ascending,
                        grid = layout.grid,
                        onSort = { setLayout(layout.copy(sort = it)) },
                        onGroup = { setLayout(layout.copy(group = it)) },
                        onToggleOrder = { setLayout(layout.copy(ascending = !layout.ascending)) },
                        onToggleGrid = { setLayout(layout.copy(grid = !layout.grid)) },
                    )
                    }
                }
            }

            when {
                !hasFolders -> item(key = "folders") {
                    Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth()) { FolderPrompt(onPickFolder) }
                }
                state.loading && state.tracks.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ShapeLoader(size = 56.dp)
                    }
                }
                state.error != null -> item(key = "error") {
                    Box(Modifier.fillParentMaxHeight(0.5f).fillMaxWidth()) { Message(state.error.ifEmpty { stringResource(R.string.library_error) }) }
                }
                state.tracks.isEmpty() -> item(key = "empty") {
                    Box(Modifier.fillParentMaxHeight(0.5f).fillMaxWidth()) {
                        Message(stringResource(R.string.library_empty_folders))
                    }
                }
                visible.isEmpty() && filters.isNotEmpty() -> item(key = "nofilter") {
                    Box(Modifier.fillParentMaxHeight(0.5f).fillMaxWidth()) { FilterEmpty(onClear = { filterMask = 0 }) }
                }
                visible.isEmpty() -> item(key = "nomatch") {
                    Box(Modifier.fillParentMaxHeight(0.4f).fillMaxWidth()) { Message(stringResource(R.string.library_no_match)) }
                }
                layout.browse != BrowseKind.Tracks -> {
                    if (layout.grid) {
                        itemsIndexed(collections.chunked(columns), key = { _, row -> "c" + row.first().id }) { _, row ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).animateItem(placementSpec = Motion.spatial()),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                row.forEach { c -> CollectionTile(c, { onOpenCollection(c.id) }, Modifier.weight(1f)) }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        itemsIndexed(collections, key = { _, c -> "c" + c.id }) { _, c ->
                            CollectionRow(c, { onOpenCollection(c.id) }, Modifier.padding(horizontal = 10.dp).animateItem(placementSpec = Motion.spatial()))
                        }
                    }
                }
                else -> groups.forEach { group ->
                    if (layout.group != GroupField.None) {
                        item(key = "group-" + group.id) {
                            GroupHeader(groupTitle(layout.group, group), group.items.size, Modifier.animateItem(placementSpec = Motion.spatial()))
                        }
                    }
                    if (layout.grid) {
                        itemsIndexed(group.items.chunked(columns), key = { _, pair -> "g" + pair.first().uri.toString() }) { rowIndex, pair ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).animateItem(placementSpec = Motion.spatial()),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                pair.forEachIndexed { i, track ->
                                    TrackTile(
                                        track, rowIndex * columns + i, verdictByTrack[track.uri.toString()], { b, c -> onOpen(track, b, c) },
                                        Modifier.weight(1f), flight,
                                        selected = track.uri.toString() in pickedIds, selecting = selecting, onToggle = { togglePick(track) },
                                    )
                                }
                                repeat(columns - pair.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        itemsIndexed(group.items, key = { _, t -> t.uri.toString() }) { index, track ->
                            TrackRow(
                                track, index, verdictByTrack[track.uri.toString()], { b, c -> onOpen(track, b, c) },
                                Modifier.padding(horizontal = 10.dp).animateItem(placementSpec = Motion.spatial()),
                                flight,
                                selected = track.uri.toString() in pickedIds, selecting = selecting, onToggle = { togglePick(track) },
                            )
                        }
                    }
                }
            }
            item(key = "bottom") {
                Spacer(Modifier.navigationBarsPadding().height(if (dockVisible) 130.dp else 110.dp))
            }
        }

        AnimatedVisibility(
            visible = fabVisible && !dockVisible && !selecting,
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
                    Text(stringResource(R.string.library_analyse), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(Motion.spatial()) { it } + fadeIn(Motion.effects()),
            exit = slideOutVertically(Motion.spatial()) { it } + fadeOut(Motion.effects()),
        ) {
            PickBar(
                count = picks.size,
                onClear = { pickedRaw = "" },
                onCompare = {
                    if (picks.size == ComparePicks.MAX) {
                        onCompare(picks[0], picks[1])
                        pickedRaw = ""
                    }
                },
                modifier = Modifier.navigationBarsPadding().padding(bottom = if (dockVisible) 112.dp else 0.dp),
            )
        }
    }
}

// ---- header --------------------------------------------------------------------------------

@Composable
private fun Header(
    onRescan: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onFolders: () -> Unit,
    onInsights: () -> Unit,
    onPlayAll: (shuffle: Boolean) -> Unit,
    canInsights: Boolean,
    canRescan: Boolean,
    canScan: Boolean,
    unscanned: Int,
    onScanAll: () -> Unit,
    brandModifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val menuTurn by animateFloatAsState(if (menu) 90f else 0f, Motion.spatial(), label = "menuTurn")
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).then(brandModifier), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box {
            ExpressiveIconButton(Icons.Rounded.MoreVert, stringResource(R.string.library_more), { menu = true }, toggled = menu, iconModifier = Modifier.rotate(menuTurn))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                if (canRescan) {
                    ExpressiveMenuItem(
                        text = { Text(stringResource(R.string.library_menu_rescan)) },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        onClick = { menu = false; onRescan() },
                    )
                }
                if (canScan) {
                    ExpressiveMenuItem(
                        text = {
                            Text(
                                if (unscanned > 0) stringResource(R.string.library_menu_scan_new, unscanned)
                                else stringResource(R.string.library_menu_scanned),
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Radar, contentDescription = null) },
                        enabled = unscanned > 0,
                        onClick = { menu = false; onScanAll() },
                    )
                }
                if (canInsights) {
                    ExpressiveMenuItem(
                        text = { Text(stringResource(R.string.library_menu_play_all)) },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                        onClick = { menu = false; onPlayAll(false) },
                    )
                    ExpressiveMenuItem(
                        text = { Text(stringResource(R.string.library_menu_shuffle_all)) },
                        leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                        onClick = { menu = false; onPlayAll(true) },
                    )
                    ExpressiveMenuItem(
                        text = { Text(stringResource(R.string.library_menu_insights)) },
                        leadingIcon = { Icon(Icons.Rounded.Insights, contentDescription = null) },
                        onClick = { menu = false; onInsights() },
                    )
                }
                ExpressiveMenuItem(
                    text = { Text(stringResource(R.string.library_menu_folders)) },
                    leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                    onClick = { menu = false; onFolders() },
                )
                ExpressiveMenuItem(
                    text = { Text(stringResource(R.string.settings_title)) },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = { menu = false; onSettings() },
                )
                ExpressiveMenuItem(
                    text = { Text(stringResource(R.string.about_title)) },
                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                    onClick = { menu = false; onAbout() },
                )
            }
        }
    }
}

/** The app mark: four bars that keep dancing out of phase. */
@Composable
internal fun EqualizerMark() {
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
                            batch.running -> stringResource(R.string.scan_running)
                            batch.cancelled -> stringResource(R.string.scan_cancelled)
                            batch.note != ScanNote.None -> stringResource(R.string.scan_stopped)
                            else -> stringResource(R.string.scan_finished)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val summary = buildList {
                        add(stringResource(R.string.scan_analysed, batch.done))
                        if (batch.skipped > 0) add(stringResource(R.string.scan_skipped, batch.skipped))
                        if (batch.flagged > 0) add(stringResource(R.string.scan_flagged, batch.flagged))
                        if (batch.failed > 0) add(stringResource(R.string.scan_failed, batch.failed))
                    }.joinToString(stringResource(R.string.scan_sep))
                    val noteText = if (batch.note == ScanNote.None) "" else stringResource(batch.note.res, batch.noteDetail)
                    Text(
                        if (batch.running && batch.note != ScanNote.None) noteText else
                        if (batch.running && batch.current.isNotEmpty()) batch.current else
                            if (batch.note != ScanNote.None) stringResource(R.string.scan_with_note, summary, noteText) else summary,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (batch.running) {
                    ExpressiveButton(stringResource(R.string.action_cancel), onCancel, kind = ButtonKind.Text)
                } else {
                    ExpressiveIconButton(Icons.Rounded.Close, stringResource(R.string.action_dismiss), onDismiss, tonal = false)
                }
            }
            if (batch.running) {
                Spacer(Modifier.height(10.dp))
                WavyProgressBar(batch.fraction, color = scheme.onSecondaryContainer, track = scheme.onSecondaryContainer.copy(alpha = 0.18f))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.scan_progress, batch.processed, batch.total), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ---- library health ---------------------------------------------------------------------------

// ---- search, filters, section bar ---------------------------------------------------------------

@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        SearchField(query, onChange, Modifier.weight(1f), placeholder = stringResource(R.string.library_search))
        trailing()
    }
}

@Composable
private fun SectionBar(
    count: Int,
    browse: BrowseKind,
    sort: SortField,
    group: GroupField,
    ascending: Boolean,
    grid: Boolean,
    onSort: (SortField) -> Unit,
    onGroup: (GroupField) -> Unit,
    onToggleOrder: () -> Unit,
    onToggleGrid: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val rotation by animateFloatAsState(if (ascending) 0f else 180f, Motion.spatial(), label = "sortArrow")
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = { (slideInVertically(Motion.spatial()) { it / 2 } + fadeIn()) togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "songCount",
        ) { n ->
            Text(
                pluralStringResource(
                    when (browse) {
                        BrowseKind.Tracks -> R.plurals.library_tracks
                        BrowseKind.Albums -> R.plurals.library_albums
                        BrowseKind.Artists -> R.plurals.library_artists
                    },
                    n, n,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (browse == BrowseKind.Tracks) Box {
            MorphSurface(
                Modifier.height(40.dp),
                corners = SpectyShapes.Pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { menu = true },
            ) {
                Box(Modifier.height(40.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(sort.labelRes()), style = MaterialTheme.typography.labelLarge)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                MenuLabel(stringResource(R.string.library_sort_by))
                SortField.entries.forEach { key ->
                    ExpressiveMenuItem(
                        text = { Text(stringResource(key.labelRes()), fontWeight = if (key == sort) FontWeight.Bold else FontWeight.Normal) },
                        checked = key == sort,
                        onClick = { menu = false; onSort(key) },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                MenuLabel(stringResource(R.string.library_group_by))
                GroupField.entries.forEach { key ->
                    ExpressiveMenuItem(
                        text = { Text(stringResource(key.labelRes()), fontWeight = if (key == group) FontWeight.Bold else FontWeight.Normal) },
                        checked = key == group,
                        onClick = { menu = false; onGroup(key) },
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        ExpressiveIconButton(
            Icons.Rounded.KeyboardArrowUp,
            stringResource(if (ascending) R.string.library_ascending else R.string.library_descending),
            { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggleOrder() },
            iconModifier = Modifier.rotate(rotation),
        )
        Spacer(Modifier.width(6.dp))
        ExpressiveIconButton(
            if (grid) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
            stringResource(if (grid) R.string.library_show_list else R.string.library_show_grid),
            { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggleGrid() },
            toggled = grid,
        )
    }
}

// ---- tracks -----------------------------------------------------------------------------------------

@Composable
internal fun CoverFallback(track: Track) {
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
internal fun TrackRow(
    track: Track,
    index: Int,
    verdict: Verdict?,
    onOpen: (Rect?, CoverSource?) -> Unit,
    modifier: Modifier = Modifier,
    flight: CoverFlight? = null,
    selected: Boolean = false,
    selecting: Boolean = false,
    onToggle: () -> Unit = {},
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
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animateColor = selecting,
        onClick = { if (selecting) onToggle() else onOpen(bounds, CoverSource(coverBounds, SpectyShapes.Cover, 180)) },
        onLongClick = onToggle,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                CoverImage(
                    uri = track.uri,
                    shape = SpectyShapes.Cover.toShape(),
                    modifier = Modifier
                        .size(60.dp)
                        .onGloballyPositioned { coverBounds = it.boundsInRoot() }
                        .graphicsLayer { alpha = if (flight?.hides(track.uri) == true) 0f else 1f },
                    sizePx = 180,
                ) { CoverFallback(track) }
                if (selected) SelectMark(Modifier.align(Alignment.BottomEnd))
            }
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
    selected: Boolean = false,
    selecting: Boolean = false,
    onToggle: () -> Unit = {},
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
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animateColor = selecting,
        onClick = { if (selecting) onToggle() else onOpen(bounds, CoverSource(coverBounds, SpectyShapes.Tile, 420)) },
        onLongClick = onToggle,
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
                if (selected) SelectMark(Modifier.align(Alignment.TopStart).padding(10.dp))
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

/** Heading of one group in the library: its name and how many tracks it holds. */
@Composable
private fun GroupHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Tick on the cover of a track picked for comparing. */
@Composable
private fun SelectMark(modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Replaces the analyse button while tracks are picked: how many, and the way to compare or let go. */
@Composable
private fun PickBar(count: Int, onClear: () -> Unit, onCompare: () -> Unit, modifier: Modifier = Modifier) {
    MorphSurface(
        modifier.fillMaxWidth().padding(16.dp),
        corners = SpectyShapes.Card,
        color = MaterialTheme.colorScheme.secondaryContainer,
        morphOnPress = false,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ExpressiveIconButton(Icons.Rounded.Close, stringResource(R.string.pick_clear), onClear, tonal = false)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.pick_count, count), style = MaterialTheme.typography.titleMedium)
                if (count < ComparePicks.MAX) {
                    Text(
                        stringResource(R.string.pick_one_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
            ExpressiveButton(stringResource(R.string.pick_compare), onCompare, enabled = count == ComparePicks.MAX)
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
private fun FolderPrompt(onPick: () -> Unit) {
    EmptyState(
        icon = Icons.Rounded.FolderOpen,
        title = stringResource(R.string.library_prompt_title),
        message = stringResource(R.string.library_prompt_message),
        modifier = Modifier.fillMaxSize(),
        action = { ExpressiveButton(stringResource(R.string.onboarding_choose_folder), onPick) },
    )
}

@Composable
private fun Message(text: String) {
    EmptyState(icon = Icons.Rounded.MusicNote, title = stringResource(R.string.library_empty_title), message = text, modifier = Modifier.fillMaxSize())
}

