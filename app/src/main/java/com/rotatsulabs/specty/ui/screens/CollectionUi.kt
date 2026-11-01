package com.rotatsulabs.specty.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import com.rotatsulabs.specty.ui.components.Reveal
import com.rotatsulabs.specty.ui.components.withoutExplicitMark
import com.rotatsulabs.specty.ui.design.components.VerdictPill
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.data.Track
import com.rotatsulabs.specty.ui.components.CoverImage
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.design.AdaptiveMath
import com.rotatsulabs.specty.ui.design.CoverFlight
import com.rotatsulabs.specty.ui.design.CoverSource
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.rememberWidthDp
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveMenuItem
import com.rotatsulabs.specty.ui.theme.Motion
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.MorphSurface

/** Only the tags matter on a page; the other fields are never read there. */
internal val TagFields = TrackFields<Track>(
    title = { it.title }, artist = { it.artist }, album = { it.album }, durationMs = { it.durationMs },
    sizeBytes = { it.sizeBytes }, modifiedSec = { it.modifiedSec }, format = { it.extension }, resultRank = { 0 },
)

/** Looks a page's collection up again from the library, so it follows rescans and never holds stale tracks. */
internal fun findCollection(tracks: List<Track>, id: String): LibraryCollection<Track>? {
    val kind = BrowseKind.entries.firstOrNull { id.startsWith(it.name + ":") } ?: return null
    val all = if (kind == BrowseKind.Artists) Browse.artists(tracks, TagFields) else Browse.albums(tracks, TagFields)
    return Browse.find(all, id)
}

@Composable
private fun LibraryCollection<Track>.title(): String = key ?: stringResource(
    if (kind == BrowseKind.Artists) R.string.group_unknown_artist else R.string.group_unknown_album,
)

@Composable
private fun LibraryCollection<Track>.subtitle(): String {
    val tracks = pluralStringResource(R.plurals.library_tracks, items.size, items.size)
    return if (kind == BrowseKind.Artists) {
        val albums = Browse.albums(items, TagFields).count { it.key != null }
        if (albums > 0) pluralStringResource(R.plurals.library_albums, albums, albums) + "  ·  " + tracks else tracks
    } else {
        val who = if (various) stringResource(R.string.album_various) else artist
        listOfNotNull(who, tracks).joinToString("  ·  ")
    }
}

@Composable
private fun CollectionCover(c: LibraryCollection<Track>, size: Modifier, sizePx: Int, feature: Boolean = false) {
    val shape = when {
        c.kind == BrowseKind.Artists -> CircleShape
        feature -> SpectyShapes.Feature.toShape()
        else -> SpectyShapes.Cover.toShape()
    }
    CoverImage(uri = c.items.first().uri, shape = shape, modifier = size, sizePx = sizePx) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (c.kind == BrowseKind.Artists) Icons.Rounded.Person else Icons.Rounded.Album, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
internal fun CollectionRow(c: LibraryCollection<Track>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MorphSurface(modifier.fillMaxWidth(), corners = SpectyShapes.Row, color = androidx.compose.ui.graphics.Color.Transparent, onClick = onClick) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CollectionCover(c, Modifier.size(60.dp), 180)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    c.subtitle(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun CollectionTile(c: LibraryCollection<Track>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MorphSurface(modifier, corners = SpectyShapes.Row, color = androidx.compose.ui.graphics.Color.Transparent, onClick = onClick) {
        Column(Modifier.padding(6.dp)) {
            CollectionCover(c, Modifier.fillMaxWidth().aspectRatio(1f), 360)
            Spacer(Modifier.height(8.dp))
            Text(c.title(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                c.subtitle(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What a page sorts its tracks by: the library's fields without Album, which is the same for every track on a page. */
private val PAGE_SORTS = SortField.entries - SortField.Album

internal fun pageFields(verdicts: Map<String, Verdict>, keyFor: (Track) -> String) = TrackFields<Track>(
    title = { it.title }, artist = { it.artist }, album = { it.album }, durationMs = { it.durationMs },
    sizeBytes = { it.sizeBytes }, modifiedSec = { it.modifiedSec }, format = { it.extension },
    resultRank = { ResultFilter.ORDER.indexOf(ResultFilter.of(verdicts[keyFor(it)])) },
)

/**
 * The page of one album or artist, laid out like a store page. An artist opens on a full-width cover that fades into
 * the background with the name over it; an album opens on a centred cover with its title and artist under it. Both
 * then show how the tracks measured, and the tracks themselves in a flat list with thin dividers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionScreen(
    collection: LibraryCollection<Track>,
    verdicts: Map<String, Verdict>,
    keyFor: (Track) -> String,
    onBack: () -> Unit,
    onOpenTrack: (Track, Rect?, CoverSource?) -> Unit,
    onOpenCollection: (String) -> Unit,
    dockVisible: Boolean,
    flight: CoverFlight?,
    modifier: Modifier = Modifier,
) {
    val artistPage = collection.kind == BrowseKind.Artists
    val albums = remember(collection) {
        if (artistPage) Browse.albums(collection.items, TagFields).filter { it.key != null } else emptyList()
    }
    var sortName by rememberSaveable(collection.id) { mutableStateOf(SortField.Title.name) }
    var ascending by rememberSaveable(collection.id) { mutableStateOf(true) }
    val sort = SortField.entries.firstOrNull { it.name == sortName } ?: SortField.Title
    val fields = remember(verdicts, keyFor) { pageFields(verdicts, keyFor) }
    val tracks = remember(collection, sort, ascending, fields) { LibraryOrder.sort(collection.items, sort, ascending, fields) }
    val summary = remember(collection, verdicts, keyFor) { ResultFilter.summary(collection.items.map { verdicts[keyFor(it)] }) }
    val listState = rememberLazyListState()
    // The title joins the top bar once the hero has scrolled away, so it is never shown twice.
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            item(key = "hero") {
                if (artistPage) ArtistHero(collection) else AlbumHero(collection, onOpenCollection)
            }
            item(key = "summary") { Summary(summary) }
            if (albums.isNotEmpty()) {
                item(key = "albumsHeader") { PageHeader(stringResource(R.string.browse_albums)) }
                items(albums, key = { it.id }) { a -> AlbumCard(a, { onOpenCollection(a.id) }) }
            }
            item(key = "tracksHeader") {
                TracksBar(
                    sort = sort, ascending = ascending,
                    onSort = { sortName = it.name }, onToggleOrder = { ascending = !ascending },
                )
            }
            items(tracks.size, key = { "t" + tracks[it].uri }) { i ->
                val t = tracks[i]
                Column(Modifier.padding(horizontal = 16.dp)) {
                    PageTrackRow(
                        track = t, number = i + 1, verdict = verdicts[keyFor(t)], thumb = artistPage, flight = flight,
                        onOpen = { bounds, cover -> onOpenTrack(t, bounds, cover) },
                    )
                    if (i < tracks.size - 1) {
                        HorizontalDivider(Modifier.padding(start = if (artistPage) 72.dp else 44.dp), color = scheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(if (dockVisible) 130.dp else 40.dp)) }
        }
        Box(Modifier.fillMaxWidth().background(if (scrolled) scheme.surface.copy(alpha = 0.95f) else androidx.compose.ui.graphics.Color.Transparent)) {
            ExpressiveTopBar(title = if (scrolled) collection.title() else "", onBack = onBack)
        }
    }
}

/** What the page holds: "1 album  ·  10 tracks  ·  38:12", the album count only on an artist page. */
@Composable
private fun LibraryCollection<Track>.stats(): String {
    val parts = ArrayList<String>()
    if (kind == BrowseKind.Artists) {
        val n = Browse.albums(items, TagFields).count { it.key != null }
        if (n > 0) parts += pluralStringResource(R.plurals.library_albums, n, n)
    }
    parts += pluralStringResource(R.plurals.library_tracks, items.size, items.size)
    val seconds = items.sumOf { it.durationMs } / 1000.0
    if (seconds > 0) parts += Fmt.duration(seconds)
    return parts.joinToString("  ·  ")
}

/** A centred cover, the title, and the artist in plain grey text that opens the artist's page. */
@Composable
private fun AlbumHero(collection: LibraryCollection<Track>, onOpenCollection: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(Modifier.statusBarsPadding().height(72.dp))
        CollectionCover(collection, Modifier.fillMaxWidth(0.72f).widthIn(max = 340.dp).aspectRatio(1f), 768, feature = true)
        Spacer(Modifier.height(10.dp))
        Text(
            collection.title(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        val artist = collection.artist
        when {
            artist != null -> Text(
                artist, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onOpenCollection(Browse.id(BrowseKind.Artists, artist)) },
            )
            collection.various -> Text(stringResource(R.string.album_various), style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
        }
        Text(collection.stats(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant.copy(alpha = 0.8f))
    }
}

/** A full-width cover that fades into the page, with the name and what the artist has over its lower edge. */
@Composable
private fun ArtistHero(collection: LibraryCollection<Track>) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val height = minOf(maxWidth, 480.dp)
        Box(Modifier.fillMaxWidth().height(height)) {
            CoverImage(
                uri = collection.items.first().uri, shape = RectangleShape, modifier = Modifier.fillMaxSize(), sizePx = 1024,
            ) {
                Surface(Modifier.fillMaxSize(), color = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, null, Modifier.size(72.dp)) }
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.45f to androidx.compose.ui.graphics.Color.Transparent,
                        1f to scheme.surface,
                    ),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    collection.title(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(collection.stats(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** The page's tracks as one bar with a chip per result, so a mostly clean album reads at a glance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(summary: List<Pair<ResultFilter, Int>>) {
    if (summary.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ResultBar(summary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { summary.forEach { (kind, n) -> ResultChip(kind, n) } }
    }
}

@Composable
private fun ResultBar(summary: List<Pair<ResultFilter, Int>>) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(0.8f).height(8.dp).clip(CircleShape)) {
        summary.forEach { (kind, n) ->
            val color = when (kind) {
                ResultFilter.Lossless, ResultFilter.HiRes, ResultFilter.Atmos -> scheme.tertiary
                ResultFilter.NeedsLook, ResultFilter.LossyFormat -> scheme.error
                else -> scheme.outline
            }
            Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(color))
        }
    }
}

/** One of an artist's albums as a rounded card: cover, name and what is on it. */
@Composable
private fun AlbumCard(c: LibraryCollection<Track>, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    MorphSurface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        corners = SpectyShapes.Card, color = scheme.surfaceContainer, onClick = onClick,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CollectionCover(c, Modifier.size(88.dp), 264)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(c.stats(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant)
        }
    }
}

/** A track in the flat list: a number (or, on an artist page, its cover), the title, and its result. */
@Composable
private fun PageTrackRow(
    track: Track,
    number: Int,
    verdict: Verdict?,
    thumb: Boolean,
    flight: CoverFlight?,
    onOpen: (Rect?, CoverSource?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var coverBounds by remember { mutableStateOf<Rect?>(null) }
    MorphSurface(
        Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() },
        corners = SpectyShapes.Row, color = androidx.compose.ui.graphics.Color.Transparent,
        onClick = { onOpen(bounds, if (thumb) CoverSource(coverBounds, SpectyShapes.Cover, 156) else null) },
    ) {
        Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (thumb) {
                CoverImage(uri = track.uri, shape = SpectyShapes.Cover.toShape(),
                    modifier = Modifier.size(52.dp).onGloballyPositioned { coverBounds = it.boundsInRoot() }
                        .graphicsLayer { alpha = if (flight?.hides(track.uri) == true) 0f else 1f },
                    sizePx = 156,
                ) {
                    Surface(Modifier.fillMaxSize(), color = scheme.tertiaryContainer) {}
                }
                Spacer(Modifier.width(16.dp))
            } else {
                Text(
                    number.toString(), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.width(28.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(track.title.withoutExplicitMark(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val parts = if (thumb) {
                    listOfNotNull(track.album?.trim()?.takeIf { it.isNotEmpty() }, Fmt.duration(track.durationMs / 1000.0))
                } else {
                    listOfNotNull(Fmt.duration(track.durationMs / 1000.0), track.extension.takeIf { it.isNotEmpty() })
                }
                Text(
                    parts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (verdict != null) {
                Spacer(Modifier.width(8.dp))
                VerdictPill(verdict)
            }
        }
    }
}

/** One kind of result and how many of the page's tracks have it. Good results use the tertiary colour, ones to check the error colour. */
@Composable
private fun ResultChip(kind: ResultFilter, count: Int) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        ResultFilter.Lossless, ResultFilter.HiRes, ResultFilter.Atmos -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ResultFilter.NeedsLook, ResultFilter.LossyFormat -> scheme.errorContainer to scheme.onErrorContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    Surface(shape = SpectyShapes.Pill.toShape(), color = container, contentColor = content) {
        Text(
            "$count  " + stringResource(kind.labelRes),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** The "Tracks" heading with its own sort, so a page can be ordered without touching the library's order. */
@Composable
private fun TracksBar(sort: SortField, ascending: Boolean, onSort: (SortField) -> Unit, onToggleOrder: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (ascending) 0f else 180f, Motion.spatial(), label = "pageSortArrow")
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.browse_tracks), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Box {
            MorphSurface(Modifier.height(36.dp), corners = SpectyShapes.Pill, color = MaterialTheme.colorScheme.secondaryContainer, onClick = { menu = true }) {
                Box(Modifier.height(36.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(sort.labelRes()), style = MaterialTheme.typography.labelLarge)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                PAGE_SORTS.forEach { key ->
                    ExpressiveMenuItem(
                        text = { Text(stringResource(key.labelRes()), fontWeight = if (key == sort) FontWeight.Bold else FontWeight.Normal) },
                        checked = key == sort,
                        onClick = { menu = false; onSort(key) },
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        ExpressiveIconButton(
            Icons.Rounded.KeyboardArrowUp,
            stringResource(if (ascending) R.string.library_ascending else R.string.library_descending),
            onToggleOrder,
            iconModifier = Modifier.rotate(rotation),
        )
    }
}

@Composable
private fun PageHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
    )
}
