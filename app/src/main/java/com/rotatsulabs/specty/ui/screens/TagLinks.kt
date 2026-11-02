package com.rotatsulabs.specty.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.audio.AudioInfo

/**
 * Lets the analysis jump to an artist's or album's page. [canOpen] says whether the library has that page, so a name
 * that matches nothing stays plain text.
 */
class TagLinks(val canOpen: (BrowseKind, String) -> Boolean, val onOpen: (BrowseKind, String) -> Unit) {
    companion object {
        val None = TagLinks({ _, _ -> false }, { _, _ -> })
    }
}

/** The value of a tag, or null when the file has none or it is blank. Keys compare without regard to case. */
internal fun AudioInfo.tag(key: String): String? =
    tags.firstOrNull { it.first.equals(key, ignoreCase = true) }?.second?.trim()?.takeIf { it.isNotEmpty() }

@Composable
internal fun TagLine(info: AudioInfo, links: TagLinks) {
    val artist = info.tag("artist")
    val album = info.tag("album")
    if (artist == null && album == null) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artist != null) TagName(artist, BrowseKind.Artists, links, Modifier.weight(1f, fill = false))
        if (artist != null && album != null) Text("·", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (album != null) TagName(album, BrowseKind.Albums, links, Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun TagName(name: String, kind: BrowseKind, links: TagLinks, modifier: Modifier) {
    val open = links.canOpen(kind, name)
    val scheme = MaterialTheme.colorScheme
    if (!open) {
        Text(
            name, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier,
        )
        return
    }
    // A page exists: the name stays plain grey text and a tap opens it.
    Text(
        name, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clickable { links.onOpen(kind, name) },
    )
}
