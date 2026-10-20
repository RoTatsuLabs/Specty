package com.rotatsulabs.specty.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** The sounds of the Easter egg. The rest of the app is silent. */
enum class Fx { Glass, Flap, Point, Hit }

/**
 * Sounds for the Easter egg, synthesised at start so the app ships no audio files. They play on the system
 * sound-effect stream, so they follow that volume and stay quiet in silent mode.
 */
object SoundFx {
    private const val RATE = 44100

    @Volatile
    private var ready = false
    private val tracks = HashMap<Fx, AudioTrack>()
    private var rumbleTrack: AudioTrack? = null

    /** Builds every sound on a background thread; calls to [play] before it finishes are ignored. */
    fun warmUp() {
        if (ready) return
        Thread {
            synchronized(this) {
                if (ready) return@Thread
                runCatching {
                    for (fx in Fx.entries) tracks[fx] = staticTrack(render(fx), loop = false)
                    rumbleTrack = staticTrack(renderRumble(), loop = true)
                }
                ready = true
            }
        }.start()
    }

    fun play(fx: Fx) {
        if (!ready) return
        val track = tracks[fx] ?: return
        runCatching {
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    /** Low rumble that deepens and swells with [level] (0 to 1); 0 or less stops it. */
    fun rumble(level: Float) {
        val track = rumbleTrack ?: return
        runCatching {
            if (level <= 0.001f) {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
                return
            }
            track.setVolume(0.05f + 0.6f * level.coerceAtMost(1f))
            track.setPlaybackRate((RATE * (0.8f + 1.4f * level.coerceAtMost(1f))).toInt())
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        }
    }

    private fun staticTrack(samples: ShortArray, loop: Boolean): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        if (loop) track.setLoopPoints(0, samples.size, -1)
        return track
    }

    // ---- synthesis -----------------------------------------------------------------------------

    private fun render(fx: Fx): ShortArray = when (fx) {
        Fx.Glass -> {
            val random = Random(5)
            val parts = ArrayList<Pair<Int, FloatArray>>()
            parts += 0 to noise(380, 0.6f, 70f, brittle = true, random = random)
            repeat(16) {
                val f = 2500f + random.nextFloat() * 4500f
                parts += random.nextInt(420) to tone(f, f, 160, 0.18f, decayMs = 40f)
            }
            mix(*parts.toTypedArray())
        }
        Fx.Flap -> pcm(tone(380f, 820f, 90, 0.35f, decayMs = 35f))
        Fx.Point -> mix(0 to tone(988f, 988f, 70, 0.3f, decayMs = 30f), 65 to tone(1319f, 1319f, 160, 0.3f, decayMs = 70f))
        Fx.Hit -> mix(0 to tone(180f, 50f, 220, 0.6f, decayMs = 80f), 0 to noise(90, 0.35f, 30f, brittle = false, random = Random(9)))
    }

    /** Whole cycles of 55, 110 and 165 Hz in 0.4 s, so the loop has no seam. */
    private fun renderRumble(): ShortArray {
        val n = RATE * 400 / 1000
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            out[i] = 0.5f * sin(2.0 * PI * 55.0 * t).toFloat() +
                0.3f * sin(2.0 * PI * 110.0 * t).toFloat() +
                0.15f * sin(2.0 * PI * 165.0 * t).toFloat()
        }
        return pcm(out)
    }

    private fun tone(from: Float, to: Float, ms: Int, volume: Float, decayMs: Float, overtone: Boolean = false): FloatArray {
        val n = RATE * ms / 1000
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val frequency = from + (to - from) * i / n
            phase += 2.0 * PI * frequency / RATE
            val millis = i * 1000f / RATE
            val envelope = min(1f, millis / 4f) * exp(-millis / decayMs)
            val wave = sin(phase) + if (overtone) 0.3 * sin(2.0 * phase) else 0.0
            out[i] = (wave * envelope * volume).toFloat()
        }
        return out
    }

    private fun noise(ms: Int, volume: Float, decayMs: Float, brittle: Boolean, random: Random): FloatArray {
        val n = RATE * ms / 1000
        val out = FloatArray(n)
        var previous = 0f
        for (i in 0 until n) {
            val sample = random.nextFloat() * 2f - 1f
            // Differencing the noise lifts the high end, which reads as glass rather than a thud.
            val shaped = if (brittle) (sample - previous) * 0.6f else sample
            previous = sample
            out[i] = shaped * exp(-(i * 1000f / RATE) / decayMs) * volume
        }
        return out
    }

    private fun mix(vararg parts: Pair<Int, FloatArray>): ShortArray {
        val length = parts.maxOf { RATE * it.first / 1000 + it.second.size }
        val out = FloatArray(length)
        for ((offsetMs, samples) in parts) {
            val start = RATE * offsetMs / 1000
            for (i in samples.indices) out[start + i] += samples[i]
        }
        return pcm(out)
    }

    private fun pcm(samples: FloatArray): ShortArray {
        val out = ShortArray(samples.size)
        val fade = min(64, samples.size)
        for (i in samples.indices) {
            var v = samples[i]
            // Ramp the last few samples to zero so a sound never ends on a click.
            if (i >= samples.size - fade && fade > 0) v *= (samples.size - i).toFloat() / fade
            out[i] = (v.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
