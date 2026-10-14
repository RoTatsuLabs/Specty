package com.rotatsulabs.specty.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.data.Track
import com.rotatsulabs.specty.ui.components.CoverImage
import com.rotatsulabs.specty.ui.LibraryState
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.theme.Motion
import kotlinx.coroutines.delay

private enum class SortKey(val label: String) {
    Title("Title"), Artist("Artist"), Duration("Duration"), Size("Size"),
}

/** The single main screen: header, search, file picker pill and the scanned library in a rounded sheet. */
@Composable
fun LibraryScreen(
    state: LibraryState,
    onLoad: (force: Boolean) -> Unit,
    onOpen: (Track) -> Unit,
    onPickFile: () -> Unit,
    onOpenSettings: () -> Unit,
    verdictFor: (Track) -> Verdict?,
    dockVisible: Boolean = false,
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

    var query by rememberSaveable { mutableStateOf("") }
    var sortIndex by rememberSaveable { mutableStateOf(0) }
    var ascending by rememberSaveable { mutableStateOf(true) }
    val sort = SortKey.entries[sortIndex]

    val visible = remember(state.tracks, query, sort, ascending) {
        val filtered = if (query.isBlank()) {
            state.tracks
        } else {
            state.tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                    (it.artist?.contains(query, ignoreCase = true) == true) ||
                    (it.album?.contains(query, ignoreCase = true) == true)
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

    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val hasTracks = granted && !state.loading && state.error == null && state.tracks.isNotEmpty()

    // One scrolling column: the header, search and picker scroll away with the list, like a collapsing app bar.
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = WindowInsets.statusBars.asPaddingValues(),
    ) {
        item(key = "header") {
            Header(onRescan = { onLoad(true) }, onSettings = onOpenSettings, canRescan = granted)
        }
        item(key = "search") { SearchPill(query, { query = it }) }
        item(key = "picker") {
            Spacer(Modifier.height(12.dp))
            PickerPill(onPickFile)
            Spacer(Modifier.height(16.dp))
        }
        item(key = "sheetTop") {
            Box(Modifier.fillMaxWidth().background(sheetColor, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))) {
                if (hasTracks) {
                    SheetHeader(
                        count = visible.size,
                        sort = sort,
                        ascending = ascending,
                        onSort = { sortIndex = it.ordinal },
                        onToggle = { ascending = !ascending },
                    )
                } else {
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
        when {
            !granted -> item(key = "permission") {
                Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().background(sheetColor)) {
                    PermissionPrompt { launcher.launch(Manifest.permission.READ_MEDIA_AUDIO) }
                }
            }
            state.loading && state.tracks.isEmpty() -> item(key = "loading") {
                Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().background(sheetColor), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(56.dp), strokeWidth = 6.dp)
                }
            }
            state.error != null -> item(key = "error") {
                Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().background(sheetColor)) { Message(state.error) }
            }
            state.tracks.isEmpty() -> item(key = "empty") {
                Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().background(sheetColor)) {
                    Message("No audio files found on this device. Use the button above to pick one.")
                }
            }
            visible.isEmpty() -> item(key = "nomatch") {
                Box(Modifier.fillParentMaxHeight(0.6f).fillMaxWidth().background(sheetColor)) {
                    Message("Nothing matches \"$query\".")
                }
            }
            else -> itemsIndexed(visible, key = { _, t -> t.uri.toString() }) { index, track ->
                Box(
                    Modifier
                        .animateItem(placementSpec = Motion.spatial())
                        .fillMaxWidth()
                        .background(sheetColor)
                        .padding(horizontal = 12.dp, vertical = 1.5.dp),
                ) {
                    TrackRow(track, index, verdictFor(track), onOpen = { onOpen(track) })
                }
            }
        }
        item(key = "bottom") {
            Spacer(Modifier.fillMaxWidth().background(sheetColor).navigationBarsPadding().height(if (dockVisible) 120.dp else 28.dp))
        }
    }
}

@Composable
private fun Header(onRescan: () -> Unit, onSettings: () -> Unit, canRescan: Boolean) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Specty",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                if (canRescan) {
                    DropdownMenuItem(
                        text = { Text("Rescan library") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        onClick = { menu = false; onRescan() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = { menu = false; onSettings() },
                )
            }
        }
    }
}

@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        singleLine = true,
        shape = CircleShape,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty(), enter = scaleIn(Motion.spatialFast()) + fadeIn(), exit = scaleOut() + fadeOut()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") }
            }
        },
        placeholder = { Text("Search songs") },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun PickerPill(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 14.dp else 28.dp, Motion.spatialFast(), label = "pickerCorner")
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, Motion.spatialFast(), label = "pickerScale")
    Surface(
        onClick = onClick,
        interactionSource = source,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(60.dp).scale(scale),
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Text("Tap to choose a file to analyse", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SheetHeader(count: Int, sort: SortKey, ascending: Boolean, onSort: (SortKey) -> Unit, onToggle: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (ascending) 0f else 180f, Motion.spatial(), label = "sortArrow")
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                (slideInVertically(Motion.spatial()) { it / 2 } + fadeIn()) togetherWith fadeOut()
            },
            modifier = Modifier.weight(1f),
            label = "songCount",
        ) { n ->
            Text(
                if (n == 1) "1 song" else "$n songs",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        // Split button: the large-radius outer ends and small-radius inner ends are the expressive signature.
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box {
                Surface(
                    onClick = { menu = true },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(Modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        Text(sort.label, style = MaterialTheme.typography.titleMedium)
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
            Surface(
                onClick = onToggle,
                modifier = Modifier.height(48.dp).width(56.dp),
                shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 24.dp, bottomEnd = 24.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = if (ascending) "Ascending" else "Descending",
                        modifier = Modifier.rotate(rotation),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, index: Int, verdict: Verdict?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    // Staggered entrance for the first screenful.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (index < 12) delay(index * 35L)
        shown = true
    }
    val appear by animateFloatAsState(if (shown) 1f else 0f, Motion.spatial(), label = "rowAppear")

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 12.dp else 24.dp, Motion.spatialFast(), label = "rowCorner")

    Surface(
        onClick = onOpen,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().graphicsLayer {
            alpha = appear.coerceIn(0f, 1f)
            translationY = (1f - appear) * 48f
        },
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(
                uri = track.uri,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(56.dp),
                sizePx = 160,
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (track.extension.isNotEmpty() && track.extension.length <= 4) {
                            Text(track.extension, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null)
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val parts = listOfNotNull(track.artist, Fmt.duration(track.durationMs / 1000.0), Fmt.size(track.sizeBytes))
                Text(
                    parts.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (verdict != null) {
                Spacer(Modifier.width(8.dp))
                VerdictBadge(verdict)
            }
        }
    }
}

@Composable
private fun VerdictBadge(verdict: Verdict) {
    val scheme = MaterialTheme.colorScheme
    val spec = when (verdict) {
        Verdict.GENUINE -> Triple("Genuine", scheme.tertiaryContainer, scheme.onTertiaryContainer)
        Verdict.LOSSY_SOURCE, Verdict.UPSAMPLED -> Triple("Fake", scheme.errorContainer, scheme.onErrorContainer)
        Verdict.POSSIBLE_LOSSY_SOURCE, Verdict.BAND_LIMITED -> Triple("Maybe", scheme.secondaryContainer, scheme.onSecondaryContainer)
        Verdict.LOSSY_CODEC -> Triple("Lossy", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
        Verdict.INCONCLUSIVE -> return
    }
    Surface(shape = CircleShape, color = spec.second, contentColor = spec.third) {
        Text(spec.first, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(72.dp),
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(32.dp)) } }
        Spacer(Modifier.height(20.dp))
        Text("Allow access to your music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Specty reads your audio files on this device to list them here. Nothing leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Grant access") }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
