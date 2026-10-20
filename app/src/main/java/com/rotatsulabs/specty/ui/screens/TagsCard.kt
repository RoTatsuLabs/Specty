package com.rotatsulabs.specty.ui.screens

import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.Pill
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.ui.components.pressScale
import com.rotatsulabs.specty.ui.theme.Motion

/** Well-known tags, shown first and with friendly names. Everything else is listed under its own key. */
private class Known(@StringRes val labelRes: Int, vararg val keys: String)

private val knownTags = listOf(
    Known(R.string.tag_title, "title"),
    Known(R.string.tag_artist, "artist"),
    Known(R.string.tag_album_artist, "album_artist", "albumartist"),
    Known(R.string.tag_album, "album"),
    Known(R.string.tag_date, "date", "year", "originaldate", "originalyear"),
    Known(R.string.tag_track, "track", "tracknumber"),
    Known(R.string.tag_disc, "disc", "discnumber"),
    Known(R.string.tag_genre, "genre"),
    Known(R.string.tag_composer, "composer"),
    Known(R.string.tag_label, "label", "publisher"),
    Known(R.string.tag_encoder, "encoder", "encoded_by", "encoding", "encodedby"),
    Known(R.string.tag_comment, "comment", "description"),
    Known(R.string.tag_isrc, "isrc"),
    Known(R.string.tag_upc, "upc", "barcode"),
    Known(R.string.tag_rating, "rating"),
    Known(R.string.tag_copyright, "copyright"),
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
        common += stringResource(k.labelRes) to hit.second
    }
    val replayGain = tags.filter { isReplayGain(it.first) && it.first.lowercase() !in used }
    used += replayGain.map { it.first.lowercase() }
    val custom = tags.filter { it.first.lowercase() !in used }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.spatial(), label = "tagsTurn")

    MorphSurface(
        modifier = Modifier.fillMaxWidth().animateContentSize(Motion.spatial()),
        corners = SpectyShapes.Card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        morphOnPress = false,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tags_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Pill("${tags.size}")
            }

            if (tags.isEmpty()) {
                Text(stringResource(R.string.tags_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    common.forEach { (label, value) -> TagRow(label, value) }
                }
            }

            if (replayGain.isNotEmpty() || custom.isNotEmpty()) {
                MorphSurface(
                    modifier = Modifier.fillMaxWidth(),
                    corners = Corners(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { expanded = !expanded },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (expanded) stringResource(R.string.tags_hide) else stringResource(R.string.tags_show_other, replayGain.size + custom.size),
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
                                SubHeader(stringResource(R.string.tags_custom))
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
    MorphSurface(
        modifier = Modifier.fillMaxWidth().animateContentSize(Motion.spatial()),
        corners = Corners(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        onClick = { open = !open },
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
