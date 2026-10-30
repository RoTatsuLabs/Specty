package com.rotatsulabs.specty.ui.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rotatsulabs.specty.data.Track
import com.rotatsulabs.specty.ui.spectrogram.PlaybackGate
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Plays a [PlayQueue] of library tracks one after another with the platform [MediaPlayer]. It only plays while the
 * app is on screen: leaving the app pauses it. A file the device cannot play is skipped.
 */
@Stable
class QueuePlayer(private val context: Context) {
    var queue by mutableStateOf<PlayQueue<Track>?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set

    val current: Track? get() = queue?.current

    private var player: MediaPlayer? = null
    private var prepared = false
    private val main = Handler(Looper.getMainLooper())

    /** Plays [tracks] from [index], in random order when [shuffle] is set. */
    fun start(tracks: List<Track>, index: Int = 0, shuffle: Boolean = false) {
        val q = PlayQueue.of(tracks, index) ?: return
        queue = if (shuffle) q.shuffled(Random.Default) else q
        load()
    }

    fun toggle() {
        val p = player ?: return
        if (playing) pause() else if (prepared) startNow(p)
    }

    fun pause() {
        if (playing) runCatching { player?.pause() }
        playing = false
    }

    fun next() {
        val q = queue?.next()
        if (q == null) stop() else { queue = q; load() }
    }

    /** Past the first seconds a press restarts the track, like most players; near the start it goes back one. */
    fun previous() {
        val q = queue ?: return
        val back = q.previous()
        if (positionMs > RESTART_AFTER_MS || back == null) {
            runCatching { player?.seekTo(0) }
            positionMs = 0L
        } else {
            queue = back
            load()
        }
    }

    fun stop() {
        releasePlayer()
        queue = null
        positionMs = 0L
    }

    fun release() = stop()

    /** Reads the current position; call it while [playing]. */
    internal fun refresh() {
        val p = player ?: return
        if (prepared) runCatching { positionMs = p.currentPosition.toLong() }
    }

    private fun load() {
        releasePlayer()
        val track = current ?: return
        positionMs = 0L
        val p = MediaPlayer()
        player = p
        runCatching {
            p.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
            )
            p.setDataSource(context, track.uri)
            p.setOnPreparedListener { if (player === p) { prepared = true; startNow(p) } }
            p.setOnCompletionListener { if (player === p) main.post { if (player === p) next() } }
            p.setOnErrorListener { _, _, _ -> if (player === p) main.post { if (player === p) next() }; true }
            p.prepareAsync()
        }.onFailure { main.post { if (player === p) next() } }
    }

    private fun startNow(p: MediaPlayer) {
        PlaybackGate.claim(this) { pause() }
        runCatching { p.start() }.onSuccess { playing = true }
    }

    private fun releasePlayer() {
        val p = player
        player = null
        prepared = false
        playing = false
        runCatching { p?.release() }
    }

    private companion object {
        const val RESTART_AFTER_MS = 3_000L
    }
}

/** A [QueuePlayer] that pauses when the app goes to the background and is released with the screen. */
@Composable
fun rememberQueuePlayer(): QueuePlayer {
    val context = LocalContext.current
    val player = remember { QueuePlayer(context.applicationContext) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    LaunchedEffect(player.playing) {
        while (player.playing) {
            player.refresh()
            delay(250)
        }
    }
    return player
}
