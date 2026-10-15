package com.rotatsulabs.specty.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.ui.components.pressScale
import com.rotatsulabs.specty.ui.theme.Motion

/** Well-known tags, shown first and with friendly names. Everything else is listed under its own key. */
private class Known(val label: String, vararg val keys: String)

private val knownTags = listOf(
    Known("Title", "title"),
    Known("Artist", "artist"),
    Known("Album artist", "album_artist", "albumartist"),
    Known("Album", "album"),
    Known("Date", "date", "year", "originaldate", "originalyear"),
    Known("Track", "track", "tracknumber"),
    Known("Disc", "disc", "discnumber"),
    Known("Genre", "genre"),
    Known("Composer", "composer"),
    Known("Label", "label", "publisher"),
    Known("Encoder", "encoder", "encoded_by", "encoding", "encodedby"),
    Known("Comment", "comment", "description"),
    Known("ISRC", "isrc"),
    Known("UPC", "upc", "barcode"),
    Known("Rating", "rating"),
    Known("Copyright", "copyright"),
)

/** True when the file's "rating" tag is 1, the explicit-content flag (0 means not explicit). */
fun isExplicit(tags: List<Pair<String, String>>): Boolean =
    tags.any { it.first.equals("rating", ignoreCase = true) && it.second.trim() == "1" }

private fun isReplayGain(key: String) = key.lowercase().startsWith("replaygain") || key.lowercase().startsWith("r128")

/** Read-only view of the file's tags: common fields first, ReplayGain next, then every custom tag as found. */
@Composable
fun TagsCard(tags: List<Pair<String, String>>) {
    val byKey = remember(tags) { tags.associateBy { it.first.lowercase() } }
    val used = HashSet<String>()
    val common = ArrayList<Pair<String, String>>()
    for (k in knownTags) {
        val hit = k.keys.firstNotNullOfOrNull { byKey[it] } ?: continue
        used += hit.first.lowercase()
        common += k.label to hit.second
    }
    val replayGain = tags.filter { isReplayGain(it.first) && it.first.lowercase() !in used }
    used += replayGain.map { it.first.lowercase() }
    val custom = tags.filter { it.first.lowercase() !in used }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.spatial(), label = "tagsTurn")
    val source = remember { MutableInteractionSource() }

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(Motion.spatial()),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tags and metadata", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                    Text("${tags.size}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }

            if (tags.isEmpty()) {
                Text("This file has no tags.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    common.forEach { (label, value) -> TagRow(label, value) }
                }
            }

            if (replayGain.isNotEmpty() || custom.isNotEmpty()) {
                Surface(
                    onClick = { expanded = !expanded },
                    interactionSource = source,
                    modifier = Modifier.fillMaxWidth().pressScale(source, 0.97f),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (expanded) "Hide other tags" else "All other tags (${replayGain.size + custom.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(turn))
                    }
                }

                if (expanded) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (replayGain.isNotEmpty()) {
                                SubHeader("ReplayGain")
                                replayGain.forEach { (k, v) -> TagRow(k, v, monospaceKey = true) }
                            }
                            if (custom.isNotEmpty()) {
                                SubHeader("Custom tags")
                                custom.forEach { (k, v) -> TagRow(k, v, monospaceKey = true) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubHeader(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

/** One key and value. Long values clamp to three lines and open fully when tapped. */
@Composable
private fun TagRow(label: String, value: String, monospaceKey: Boolean = false) {
    var open by rememberSaveable(label, value) { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    Surface(
        onClick = { open = !open },
        interactionSource = source,
        modifier = Modifier.fillMaxWidth().pressScale(source, 0.98f).animateContentSize(Motion.spatial()),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = if (monospaceKey) FontFamily.Monospace else null,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(112.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (open) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
