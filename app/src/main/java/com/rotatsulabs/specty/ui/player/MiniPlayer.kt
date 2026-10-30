package com.rotatsulabs.specty.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.data.Track
import com.rotatsulabs.specty.ui.components.CoverImage
import com.rotatsulabs.specty.ui.components.withoutExplicitMark
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.MorphSurface

/** The track that is playing, with previous, play or pause, next and close. Tapping the track opens its analysis. */
@Composable
fun MiniPlayer(player: QueuePlayer, track: Track, onOpen: (Track) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    MorphSurface(modifier.fillMaxWidth(), corners = SpectyShapes.Card, color = scheme.secondaryContainer, onClick = { onOpen(track) }) {
        Column {
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(uri = track.uri, shape = SpectyShapes.Cover.toShape(), modifier = Modifier.size(44.dp), sizePx = 132) {
                    Surface(Modifier.fillMaxSize(), color = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(22.dp)) }
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title.withoutExplicitMark(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSecondaryContainer)
                    track.artist?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSecondaryContainer.copy(alpha = 0.72f))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExpressiveIconButton(Icons.Rounded.SkipPrevious, stringResource(R.string.player_previous), player::previous, tonal = false, size = 40.dp)
                    ExpressiveIconButton(
                        if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (player.playing) R.string.player_pause else R.string.player_play), player::toggle, size = 40.dp,
                    )
                    ExpressiveIconButton(Icons.Rounded.SkipNext, stringResource(R.string.player_next), player::next, tonal = false, size = 40.dp)
                    ExpressiveIconButton(Icons.Rounded.Close, stringResource(R.string.player_close), player::stop, tonal = false, size = 40.dp)
                }
            }
            val fraction = if (track.durationMs > 0) (player.positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                color = scheme.primary, trackColor = scheme.onSecondaryContainer.copy(alpha = 0.12f),
            )
        }
    }
}
