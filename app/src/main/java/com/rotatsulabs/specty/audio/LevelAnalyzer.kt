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
    private const val TIMELINE_POINTS = 240

    fun toDb(linear: Double): Double = if (linear <= 1e-10) -200.0 else 20.0 * log10(linear)

    fun analyze(pcm: PcmFile, onProgress: (Float) -> Unit = {}): LevelStats {
        val channels = pcm.channels
        val peaks = DoubleArray(channels)
        val sumSquares = DoubleArray(channels)
        val runs = IntArray(channels)
        val loudness = LoudnessMeter(pcm.sampleRate, channels)
        val dynamics = DynamicsMeter(channels, pcm.sampleRate)
        val slicePeaks = DoubleArray(TIMELINE_POINTS)
        val sliceClips = IntArray(TIMELINE_POINTS)
        var clipped = 0L
        var bitMask = 0L
        var frames = 0L
        val total = pcm.frames.coerceAtLeast(1)

        // True peak is the expensive part and channels are independent, so each channel gets its own thread.
        val tpDone = Array(channels) { AtomicLong() }
        val tpPeaks = DoubleArray(channels)
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(channels.coerceAtMost(8))
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
                    bitMask = bitMask or Math.rint(a * SCALE_24).toLong()
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
        val effectiveBits = if (bitMask == 0L) {
            null
        } else {
            (24 - java.lang.Long.numberOfTrailingZeros(bitMask).coerceAtMost(23)).coerceAtLeast(1)
        }

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
            effectiveBitDepth = effectiveBits,
            channels = perChannel,
            drTt = dynamics.dynamicRange(),
            dcOffsets = dynamics.dcOffsets(),
            correlation = dynamics.correlation(),
            balanceDb = dynamics.balanceDb(),
            timeline = timeline,
        )
    }
}
