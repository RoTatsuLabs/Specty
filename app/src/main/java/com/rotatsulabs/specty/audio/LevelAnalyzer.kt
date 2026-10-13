package com.rotatsulabs.specty.audio

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Single streaming pass computing peak, RMS, LUFS, true peak, clipping and effective bit depth. */
object LevelAnalyzer {
    /** Samples at or above this level (about 16-bit full scale) count towards clipping runs. */
    private const val CLIP_LEVEL = 0.99997
    private const val MIN_CLIP_RUN = 3
    private const val SCALE_24 = 8_388_608.0

    fun toDb(linear: Double): Double = if (linear <= 1e-10) -200.0 else 20.0 * log10(linear)

    fun analyze(pcm: PcmFile, onProgress: (Float) -> Unit = {}): LevelStats {
        val channels = pcm.channels
        val peaks = DoubleArray(channels)
        val sumSquares = DoubleArray(channels)
        val runs = IntArray(channels)
        val loudness = LoudnessMeter(pcm.sampleRate, channels)
        val truePeak = TruePeakMeter(channels, pcm.sampleRate)
        var clipped = 0L
        var bitMask = 0L
        var frames = 0L
        val total = pcm.frames.coerceAtLeast(1)

        pcm.forEachChunk { buf, n ->
            for (f in 0 until n) {
                val base = f * channels
                for (c in 0 until channels) {
                    val a = abs(buf[base + c].toDouble())
                    if (a > peaks[c]) peaks[c] = a
                    sumSquares[c] += a * a
                    if (a >= CLIP_LEVEL) {
                        runs[c]++
                        if (runs[c] == MIN_CLIP_RUN) clipped += MIN_CLIP_RUN else if (runs[c] > MIN_CLIP_RUN) clipped++
                    } else {
                        runs[c] = 0
                    }
                    bitMask = bitMask or Math.rint(a * SCALE_24).toLong()
                }
            }
            loudness.process(buf, n)
            truePeak.process(buf, n)
            frames += n
            onProgress((frames.toDouble() / total).toFloat().coerceIn(0f, 1f))
        }

        if (frames == 0L) throw IllegalStateException("The decoder produced no audio samples")

        var overallPeak = 0.0
        var overallSquares = 0.0
        val perChannel = ArrayList<ChannelStats>(channels)
        for (c in 0 until channels) {
            overallPeak = max(overallPeak, peaks[c])
            overallSquares += sumSquares[c]
            perChannel += ChannelStats(
                peakDb = toDb(peaks[c]),
                rmsDb = toDb(sqrt(sumSquares[c] / frames)),
            )
        }
        val overallRms = sqrt(overallSquares / (frames * channels))
        val effectiveBits = if (bitMask == 0L) {
            null
        } else {
            (24 - java.lang.Long.numberOfTrailingZeros(bitMask).coerceAtMost(23)).coerceAtLeast(1)
        }

        return LevelStats(
            peakDb = toDb(overallPeak),
            rmsDb = toDb(overallRms),
            lufs = loudness.integratedLufs(),
            truePeakDbtp = toDb(truePeak.peak),
            clippedSamples = clipped,
            framesPerChannel = frames,
            effectiveBitDepth = effectiveBits,
            channels = perChannel,
        )
    }
}
