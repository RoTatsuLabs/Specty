package com.rotatsulabs.specty.audio

import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Looks for content above the Nyquist of a 44.1 or 48 kHz source in the peak-hold spectrum. The average spectrum
 * is a mean of dB over frames, so a cymbal that sounds in a few frames barely moves it; the peak-hold level does.
 * Used for that one question only. The cliff and the shape of the spectrum are still read from the average.
 */
object TopContent {
    /** Above the highest source Nyquist (48 kHz) a file can carry. */
    const val FROM_HZ = 24_500.0

    /** The peak-hold level must stand this far above what the file's own noise floor would reach. */
    const val MARGIN_DB = 10.0

    private const val MIN_FRAMES = 20
    private const val MIN_BINS = 16

    /**
     * Median peak-hold level from [FROM_HZ] to [upToHz] (top of the band when null), less what the file's own noise
     * floor ([floorVariance]) would reach. Near zero for a floor alone. Null when the band is too narrow, the floor
     * is unknown or there are too few frames.
     */
    fun marginDb(hold: PeakHold?, floorVariance: Double?, sampleRate: Int, fftSize: Int, upToHz: Double? = null): Double? {
        if (hold == null || hold.frames < MIN_FRAMES) return null
        if (floorVariance == null || floorVariance.isNaN() || floorVariance <= 0.0) return null
        val bins = hold.db.size
        val binHz = sampleRate.toDouble() / fftSize
        val from = (FROM_HZ / binHz).roundToInt()
        val to = (minOf(sampleRate / 2.0 * 0.99, upToHz ?: Double.MAX_VALUE) / binHz).toInt().coerceAtMost(bins - 1)
        if (to - from < MIN_BINS) return null
        val radius = max(1, (65.0 / binHz).roundToInt())
        val level = DoubleArray(to - from + 1)
        for (k in from..to) {
            val lo = max(0, k - radius)
            val hi = minOf(bins - 1, k + radius)
            var sum = 0.0
            for (j in lo..hi) sum += hold.db[j]
            level[k - from] = sum / (hi - lo + 1)
        }
        level.sort()
        return level[level.size / 2] - floorPeakDb(hold, floorVariance, fftSize)
    }

    /**
     * Per-bin level (dB) that white noise of [variance] reaches in the same share of frames as the peak-hold level:
     * the power of one bin of white noise is exponential, so the level that 1 frame in 100 exceeds is ln(100) times its mean.
     */
    fun floorPeakDb(hold: PeakHold, variance: Double, fftSize: Int): Double {
        val w = WindowFn.Hann.coefficients(fftSize)
        val sum = w.sum()
        val binPower = variance * 4.0 * w.sumOf { it * it } / (sum * sum)
        return 10.0 * log10(binPower) + 10.0 * log10(-ln(hold.rank.toDouble() / (hold.frames + 1)))
    }
}
