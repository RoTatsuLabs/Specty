package com.rotatsulabs.specty.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.data.Track
import com.rotatsulabs.specty.ui.components.CoverImage
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.design.CoverFlight
import com.rotatsulabs.specty.ui.design.CoverSource
import com.rotatsulabs.specty.ui.design.SpectyShapes
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
private fun CollectionCover(c: LibraryCollection<Track>, size: Modifier, sizePx: Int) {
    val shape = if (c.kind == BrowseKind.Artists) CircleShape else SpectyShapes.Cover.toShape()
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

/**
 * The page of one album or artist: a header, then (for an artist) its albums, then the tracks. A track opens its
 * analysis the same way it does from the library.
 */
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
    val albums = remember(collection) {
        if (collection.kind == BrowseKind.Artists) Browse.albums(collection.items, TagFields) else emptyList()
    }
    val tracks = remember(collection) { LibraryOrder.sort(collection.items, SortField.Title, true, TagFields) }
    val totalSec = remember(collection) { collection.items.sumOf { it.durationMs } / 1000.0 }
    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(title = collection.title(), subtitle = collection.subtitle(), onBack = onBack)
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            item(key = "head") {
                Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CollectionCover(collection, Modifier.size(132.dp), 400)
                    Spacer(Modifier.width(20.dp))
                    Column {
                        Text(collection.title(), style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            collection.subtitle(), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (totalSec > 0) {
                            Text(Fmt.duration(totalSec), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (albums.any { it.key != null }) {
                item(key = "albumsHeader") { PageHeader(stringResource(R.string.browse_albums)) }
                items(albums.filter { it.key != null }, key = { "a" + it.id }) { a ->
                    CollectionRow(a, { onOpenCollection(a.id) }, Modifier.padding(horizontal = 10.dp))
                }
            }
            item(key = "tracksHeader") { PageHeader(stringResource(R.string.browse_tracks)) }
            items(tracks.size, key = { "t" + tracks[it].uri }) { i ->
                val t = tracks[i]
                TrackRow(
                    t, i, verdicts[keyFor(t)], { b, c -> onOpenTrack(t, b, c) },
                    Modifier.padding(horizontal = 10.dp), flight,
                )
            }
            item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(if (dockVisible) 130.dp else 40.dp)) }
        }
    }
}

@Composable
private fun PageHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
    )
}
