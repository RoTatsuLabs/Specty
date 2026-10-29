package com.rotatsulabs.specty.audio

import kotlin.math.log10
import kotlin.math.min
import kotlin.math.sqrt

/** Average spectra in dB per bin, for the whole track and for equal sections of it. */
class AuthSpectra(
    val sampleRate: Int,
    val fftSize: Int,
    val whole: FloatArray,
    /** One average per section of the track; null for sections without audible content. Empty for very short files. */
    val sections: List<FloatArray?>,
    /** White-noise equivalent power per channel (full scale +-1.0) of the quietest passages, or null when none qualified. */
    val quietFloorVariance: Double? = null,
)

/**
 * Builds the spectra the authenticity verdict is judged on. They use one fixed Hann transform of
 * [AnalysisVersion.AUTH_FFT] and unquantised values, so the verdict does not move with the spectrogram size or window
 * chosen in Settings, and does not depend on how the spectrogram happens to be stored.
 */
object AuthSpectrumAnalyzer {
    const val SECTIONS = 24
    private const val MAX_COLUMNS = 1200
    private const val MIN_COLUMNS_PER_SECTION = 6

    /** Frames quieter than this (dBFS, summed power) are left out of the averages. */
    private const val ACTIVE_FRAME_DB = -70.0

    fun compute(pcm: PcmFile, onProgress: (Float) -> Unit = {}): AuthSpectra {
        val fftSize = AnalysisVersion.AUTH_FFT
        val channels = pcm.channels
        val bins = fftSize / 2
        val total = pcm.frames
        val columns = if (total <= fftSize) 1 else min(MAX_COLUMNS.toLong(), (total - fftSize) / (fftSize / 2) + 1).toInt()
        val sectionCount = min(SECTIONS, columns / MIN_COLUMNS_PER_SECTION).let { if (it < 2) 0 else it }

        val fft = Fft(fftSize)
        val window = WindowFn.Hann.coefficients(fftSize)
        val windowSum = window.sum()
        val buffer = FloatArray(fftSize * channels)
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        val mixPower = DoubleArray(bins)
        val db = DoubleArray(bins)

        val activeSum = DoubleArray(bins)
        var activeCount = 0
        val allSum = DoubleArray(bins)
        val sectionSum = Array(sectionCount) { DoubleArray(bins) }
        val sectionActive = IntArray(sectionCount)

        // Noise floor of the quietest frames (see NoiseFloor): median band power per frame, in raw |X|^2 units.
        val windowPower = window.sumOf { it * it }
        val rawScale = (windowSum / 2.0) * (windowSum / 2.0)
        val binHz = pcm.sampleRate.toDouble() / fftSize
        val floorLow = Math.ceil(NoiseFloor.BAND_LOW_HZ / binHz).toInt()
        val floorHigh = min((NoiseFloor.BAND_HIGH_HZ / binHz).toInt(), bins - 1)
        val floorBand = DoubleArray(maxOf(0, floorHigh - floorLow + 1))
        val floorFrames = ArrayList<DoubleArray>()

        for (col in 0 until columns) {
            val start = if (columns == 1) 0L else Math.round(col.toDouble() * (total - fftSize) / (columns - 1))
            pcm.readFrames(start, fftSize, buffer)
            mixPower.fill(0.0)
            for (c in 0 until channels) {
                for (i in 0 until fftSize) {
                    re[i] = buffer[i * channels + c] * window[i]
                    im[i] = 0.0
                }
                fft.transform(re, im)
                for (k in 0 until bins) {
                    val magnitude = sqrt(re[k] * re[k] + im[k] * im[k]) * 2.0 / windowSum
                    mixPower[k] += magnitude * magnitude
                }
            }
            var framePower = 0.0
            for (k in 0 until bins) {
                val power = mixPower[k] / channels
                framePower += power
                db[k] = toDb(power)
                allSum[k] += db[k]
            }
            if (floorBand.isNotEmpty()) {
                var sum = 0.0
                for (k in floorLow..floorHigh) {
                    val raw = mixPower[k] / channels * rawScale
                    floorBand[k - floorLow] = raw
                    sum += raw
                }
                if (sum > 0.0) {
                    val sorted = floorBand.copyOf()
                    sorted.sort()
                    val mid = sorted.size / 2
                    val median = if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
                    floorFrames.add(doubleArrayOf(sum / sorted.size, median))
                }
            }
            if (toDb(framePower) > ACTIVE_FRAME_DB) {
                for (k in 0 until bins) activeSum[k] += db[k]
                activeCount++
                if (sectionCount > 0) {
                    val section = (col.toLong() * sectionCount / columns).toInt()
                    for (k in 0 until bins) sectionSum[section][k] += db[k]
                    sectionActive[section]++
                }
            }
            if (col % 16 == 0) onProgress(col.toFloat() / columns)
        }

        val whole = FloatArray(bins)
        if (activeCount > 0) {
            for (k in 0 until bins) whole[k] = (activeSum[k] / activeCount).toFloat()
        } else {
            for (k in 0 until bins) whole[k] = (allSum[k] / columns).toFloat()
        }
        val sections = List(sectionCount) { s ->
            if (sectionActive[s] == 0) {
                null
            } else {
                FloatArray(bins) { k -> (sectionSum[s][k] / sectionActive[s]).toFloat() }
            }
        }
        return AuthSpectra(pcm.sampleRate, fftSize, whole, sections, quietFloor(floorFrames, windowPower))
    }

    /**
     * |X|^2 of white noise is exponential with mean sigma^2 * sum(w^2), so its median is ln 2 times that; the median
     * ignores tonal peaks. Read in the quietest [NoiseFloor.QUIET_FRACTION] of the frames. Method from Mornye.
     */
    private fun quietFloor(frames: List<DoubleArray>, windowPower: Double): Double? {
        if (frames.isEmpty()) return null
        val quiet = frames.sortedBy { it[0] }.take(maxOf(1, (frames.size * NoiseFloor.QUIET_FRACTION).toInt()))
        val estimates = quiet.map { it[1] / Math.log(2.0) / windowPower }.sorted()
        val mid = estimates.size / 2
        return if (estimates.size % 2 == 1) estimates[mid] else (estimates[mid - 1] + estimates[mid]) / 2.0
    }

    private fun toDb(power: Double): Double = 10.0 * log10(power + 1e-18)
}
