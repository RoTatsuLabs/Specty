package com.rotatsulabs.specty.ui.spectrogram

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

/**
 * Plays the analysed file with the platform [MediaPlayer] so the spectrogram can show where the sound is. The player
 * is created on first use and released with the screen. A format the device cannot play sets [failed].
 */
@Stable
class Playback(private val context: Context, private val uri: Uri) {
    var playing by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var failed by mutableStateOf(false)
        private set

    private var player: MediaPlayer? = null
    private var prepared = false
    private var wantPlay = false
    private var pendingSeek = -1

    private fun create(): MediaPlayer? {
        player?.let { return it }
        if (failed) return null
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(context, uri)
                setOnPreparedListener {
                    prepared = true
                    if (pendingSeek >= 0) it.seekTo(pendingSeek)
                    pendingSeek = -1
                    if (wantPlay) startNow(it)
                }
                setOnCompletionListener {
                    playing = false
                    wantPlay = false
                    it.seekTo(0)
                    positionMs = 0L
                }
                setOnErrorListener { _, _, _ ->
                    fail()
                    true
                }
                prepareAsync()
            }
        }.getOrElse {
            fail()
            null
        }.also { player = it }
    }

    private fun startNow(p: MediaPlayer) {
        p.start()
        playing = true
    }

    private fun fail() {
        failed = true
        playing = false
        wantPlay = false
        prepared = false
        runCatching { player?.release() }
        player = null
    }

    fun toggle() {
        if (playing || wantPlay) pause() else play()
    }

    fun play() {
        val p = create() ?: return
        wantPlay = true
        if (prepared) startNow(p)
    }

    fun pause() {
        wantPlay = false
        if (playing) runCatching { player?.pause() }
        playing = false
    }

    /** Jumps to [fraction] (0 to 1) of [durationMs]. Works before the first play, too. */
    fun seekTo(fraction: Float, durationMs: Long) {
        val to = (fraction.coerceIn(0f, 1f) * durationMs).toLong().toInt()
        positionMs = to.toLong()
        val p = create() ?: return
        if (prepared) p.seekTo(to) else pendingSeek = to
    }

    /** Reads the current position; call it while [playing]. */
    internal fun refresh() {
        val p = player ?: return
        if (prepared) runCatching { positionMs = p.currentPosition.toLong() }
    }

    fun release() {
        wantPlay = false
        playing = false
        prepared = false
        runCatching { player?.release() }
        player = null
    }
}

/** A [Playback] for [uri] that stops when the app goes to the background and is released with the screen. */
@Composable
fun rememberPlayback(uri: Uri): Playback {
    val context = LocalContext.current
    val playback = remember(uri) { Playback(context.applicationContext, uri) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(playback, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) playback.pause() }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            playback.release()
        }
    }
    LaunchedEffect(playback.playing) {
        while (playback.playing) {
            playback.refresh()
            delay(40)
        }
    }
    return playback
}
