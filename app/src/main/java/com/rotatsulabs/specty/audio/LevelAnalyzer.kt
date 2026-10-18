package com.rotatsulabs.specty.audio

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Single streaming pass computing peak, RMS, LUFS, true peak, clipping and effective bit depth. */
object LevelAnalyzer {
    /** Samples at or above this level count towards clipping runs. Just under +32767/32768, the 16-bit positive limit. */
    private const val CLIP_LEVEL = 0.99996
    private const val MIN_CLIP_RUN = 3
    private const val SCALE_24 = 8_388_608.0

    /** Share of samples allowed to break a zero-padding pattern before it stops counting (one stray sample must not decide). */
    private const val LSB_TOLERANCE = 1e-3
    private const val MIN_LSB_SAMPLES = 4096
    /** Levels 1..GRID_LEVELS (24-bit units, below -48 dBFS) are checked for a coarse grid, e.g. 16-bit audio after a gain change. */
    private const val GRID_LEVELS = 1 shl 16
    /** Enough samples in that range that every level would be hit several times if the signal really had 24 bits. */
    private const val GRID_MIN_SAMPLES = 5L * GRID_LEVELS
    private const val GRID_MAX_FILL = 0.25
    private const val TIMELINE_POINTS = 240

    fun toDb(linear: Double): Double = if (linear <= 1e-10) -200.0 else 20.0 * log10(linear)

    /** [maxThreads] bounds the true-peak workers; a background scan uses fewer so the phone stays cool. */
    fun analyze(pcm: PcmFile, maxThreads: Int = 8, onProgress: (Float) -> Unit = {}): LevelStats {
        val channels = pcm.channels
        val peaks = DoubleArray(channels)
        val sumSquares = DoubleArray(channels)
        val runs = IntArray(channels)
        val loudness = LoudnessMeter(pcm.sampleRate, channels)
        val dynamics = DynamicsMeter(channels, pcm.sampleRate)
        val slicePeaks = DoubleArray(TIMELINE_POINTS)
        val sliceClips = IntArray(TIMELINE_POINTS)
        var clipped = 0L
        val trailingZeros = LongArray(25)
        var nonZero = 0L
        val gridSeen = java.util.BitSet(GRID_LEVELS + 1)
        var gridSamples = 0L
        var frames = 0L
        val total = pcm.frames.coerceAtLeast(1)

        // True peak is the expensive part and channels are independent, so each channel gets its own thread.
        val tpDone = Array(channels) { AtomicLong() }
        val tpPeaks = DoubleArray(channels)
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(channels.coerceIn(1, maxThreads.coerceAtLeast(1)))
        val futures = (0 until channels).map { c ->
            pool.submit(
                Callable {
                    val meter = TruePeakChannel(pcm.sampleRate)
                    PcmFile(pcm.file, channels, pcm.sampleRate).use { own ->
                        own.forEachChunk { buf, n ->
                            if (cancelled.get()) return@forEachChunk
                            meter.process(buf, n, channels, c)
                            tpDone[c].addAndGet(n.toLong())
                        }
                    }
                    tpPeaks[c] = meter.peak
                },
            )
        }

        try {
        pcm.forEachChunk { buf, n ->
            for (f in 0 until n) {
                val base = f * channels
                val slice = ((frames + f) * TIMELINE_POINTS / total).toInt().coerceAtMost(TIMELINE_POINTS - 1)
                for (c in 0 until channels) {
                    val a = abs(buf[base + c].toDouble())
                    if (a > slicePeaks[slice]) slicePeaks[slice] = a
                    if (a > peaks[c]) peaks[c] = a
                    sumSquares[c] += a * a
                    if (a >= CLIP_LEVEL) {
                        runs[c]++
                        if (runs[c] == MIN_CLIP_RUN) {
                            clipped += MIN_CLIP_RUN
                            sliceClips[slice] += MIN_CLIP_RUN
                        } else if (runs[c] > MIN_CLIP_RUN) {
                            clipped++
                            sliceClips[slice]++
                        }
                    } else {
                        runs[c] = 0
                    }
                    val level = Math.rint(a * SCALE_24).toLong()
                    if (level != 0L) {
                        nonZero++
                        trailingZeros[java.lang.Long.numberOfTrailingZeros(level).coerceAtMost(24)]++
                        if (level <= GRID_LEVELS) {
                            gridSeen.set(level.toInt())
                            gridSamples++
                        }
                    }
                }
            }
            loudness.process(buf, n)
            dynamics.process(buf, n)
            frames += n
            val tp = tpDone.sumOf { it.get() }.toDouble() / (total * channels)
            onProgress(((frames.toDouble() / total) * 0.4 + tp * 0.6).toFloat().coerceIn(0f, 1f))
        }
        for (f in futures) {
            try {
                f.get()
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
        } catch (t: Throwable) {
            cancelled.set(true)
            throw t
        } finally {
            pool.shutdownNow()
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
        val depth = estimateBitDepth(trailingZeros, nonZero, gridSeen.cardinality(), gridSamples)

        val durationSec = frames.toDouble() / pcm.sampleRate
        val timeline = Timeline(
            loudness = loudness.series(TIMELINE_POINTS, durationSec),
            peakDb = FloatArray(TIMELINE_POINTS) { toDb(slicePeaks[it]).coerceAtLeast(-120.0).toFloat() },
            clips = sliceClips,
            durationSec = durationSec,
        )

        return LevelStats(
            peakDb = toDb(overallPeak),
            rmsDb = toDb(overallRms),
            lufs = loudness.integratedLufs(),
            truePeakDbtp = toDb(tpPeaks.max()),
            clippedSamples = clipped,
            framesPerChannel = frames,
            effectiveBitDepth = depth.first,
            channels = perChannel,
            drTt = dynamics.dynamicRange(),
            dcOffsets = dynamics.dcOffsets(),
            correlation = dynamics.correlation(),
            balanceDb = dynamics.balanceDb(),
            timeline = timeline,
            bitDepthBasis = depth.second,
        )
    }

    /**
     * Real resolution of decoded float samples, scaled to 24-bit integer levels.
     *
     * Padding shows as low bits that are zero in (almost) every sample. A gain change after the padding breaks that
     * pattern but leaves the samples on a coarse grid, so the small-level range is also checked for gaps. Resampling,
     * dither added at 24 bits and float sources fill every level; they read as "all bits in use", which says the
     * samples are precise, not that the audio was recorded at that depth. Too few samples give no answer.
     */
    internal fun estimateBitDepth(
        trailingZeros: LongArray,
        nonZero: Long,
        gridLevelsSeen: Int,
        gridSamples: Long,
    ): Pair<Int?, BitDepthBasis> {
        if (nonZero < MIN_LSB_SAMPLES) return null to BitDepthBasis.UNKNOWN
        var below = 0L
        var padded = 0
        for (p in 0..23) {
            if (p > 0) below += trailingZeros[p - 1]
            if (below.toDouble() / nonZero <= LSB_TOLERANCE) padded = p else break
        }
        var bits = 24 - padded
        var basis = if (padded > 0) BitDepthBasis.ZERO_PADDED else BitDepthBasis.ALL_BITS

        if (padded == 0 && gridSamples >= GRID_MIN_SAMPLES) {
            val fill = gridLevelsSeen.toDouble() / GRID_LEVELS
            if (fill in 1e-9..GRID_MAX_FILL) {
                val gridBits = 24 - Math.round(kotlin.math.ln(1.0 / fill) / kotlin.math.ln(2.0)).toInt()
                if (gridBits < bits) {
                    bits = gridBits.coerceAtLeast(1)
                    basis = BitDepthBasis.LEVEL_GRID
                }
            }
        }
        return bits to basis
    }
}
