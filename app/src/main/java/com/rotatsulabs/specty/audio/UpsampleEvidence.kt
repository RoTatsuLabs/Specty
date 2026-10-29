package com.rotatsulabs.specty.audio

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Exact fingerprints of upsampling by a whole ratio from 44.1 or 48 kHz: every sample repeated (sample-and-hold) or
 * the in-between samples on a straight line (linear interpolation). Both leave a pattern a genuine recording does not.
 *
 * Adapted from the Hi-Res checks in Mornye (https://github.com/mornyeapp/Mornye,
 * `rust_backend/crates/core/src/media/hires/evidence.rs`), LGPL-3.0. Ported to Kotlin, changed to read several windows
 * of the decoded file, and combined with Specty under GPL-3.0. See About > Open source licences.
 */
object UpsampleEvidence {
    enum class Kind { SAMPLE_HOLD, LINEAR_INTERPOLATION }

    class Finding(val kind: Kind, val ratio: Int, val sourceHz: Int)

    private val SOURCE_RATES = intArrayOf(44_100, 48_000)

    /** The signal must move at this many original samples, and (almost) never between them. */
    private const val MIN_MOVING_ANCHORS = 1000
    private const val MAX_VIOLATION_RATE = 0.001

    private const val WINDOW_FRAMES = 1 shl 16
    private const val MAX_WINDOWS = 6

    /** Decoded float PCM carries up to 24 bits, so this scale turns it back into whole sample values. */
    private const val SCALE = 8_388_608.0

    /**
     * Looks at windows spread over the file, on the first channel. A finding needs every window with audible content
     * to show the same artifact; a file that is only partly upsampled gets none.
     */
    fun scan(pcm: PcmFile, ensureActive: () -> Unit = {}): Finding? {
        val rate = pcm.sampleRate
        if (SOURCE_RATES.none { rate % it == 0 && rate / it in 2..8 }) return null
        if (pcm.frames < 4) return null
        val window = min(pcm.frames, WINDOW_FRAMES.toLong()).toInt()
        val count = if (pcm.frames <= window) 1 else min(MAX_WINDOWS.toLong(), pcm.frames / window).toInt()
        val buffer = FloatArray(window * pcm.channels)
        val samples = IntArray(window)
        var active = 0
        var found: Finding? = null
        for (w in 0 until count) {
            ensureActive()
            val start = if (count == 1) 0L else (pcm.frames - window) * w / (count - 1)
            val got = pcm.readFrames(start, window, buffer)
            var or = 0
            for (i in 0 until got) {
                val v = (buffer[i * pcm.channels] * SCALE).roundToInt().coerceIn(-8_388_608, 8_388_607)
                samples[i] = v
                or = or or v
            }
            if (or == 0 || got < 4) continue
            active++
            val hit = detect(samples, got, or, rate) ?: return null
            if (found != null && (found.kind != hit.kind || found.ratio != hit.ratio)) return null
            found = hit
        }
        return found.takeIf { active > 0 }
    }

    /** One window of one channel. [or] is the OR of all its samples, which shows the bits actually used. */
    fun detect(samples: IntArray, length: Int, or: Int, sampleRate: Int): Finding? {
        if (or == 0 || length < 4) return null
        // Measured in units of the bits in use, so a padded source's rounding stays within a couple of its own LSBs.
        val unused = Integer.numberOfTrailingZeros(or)
        for (source in SOURCE_RATES) {
            if (sampleRate % source != 0) continue
            val ratio = sampleRate / source
            if (ratio !in 2..8) continue
            for (phase in 0 until ratio) {
                val kind = atPhase(samples, length, unused, ratio, phase)
                if (kind != null) return Finding(kind, ratio, source)
            }
        }
        return null
    }

    private fun atPhase(samples: IntArray, length: Int, unused: Int, ratio: Int, phase: Int): Kind? {
        var inner = 0
        var holdViolations = 0
        var holdMoving = 0
        var lineViolations = 0
        var lineMoving = 0
        for (i in 1 until length - 1) {
            val d1 = (samples[i] shr unused) - (samples[i - 1] shr unused)
            val d2 = abs((samples[i - 1] shr unused) - 2 * (samples[i] shr unused) + (samples[i + 1] shr unused))
            if ((i + ratio - phase) % ratio != 0) {
                // Between two original samples.
                inner++
                if (d1 != 0) holdViolations++
                if (d2 > 2) lineViolations++
            } else {
                // An original sample.
                if (d1 != 0) holdMoving++
                if (d2 > 2) lineMoving++
            }
        }
        val allowed = inner * MAX_VIOLATION_RATE
        if (holdMoving >= MIN_MOVING_ANCHORS && holdViolations <= allowed) return Kind.SAMPLE_HOLD
        if (lineMoving >= MIN_MOVING_ANCHORS && lineViolations <= allowed) return Kind.LINEAR_INTERPOLATION
        return null
    }
}
