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
        return AuthSpectra(pcm.sampleRate, fftSize, whole, sections)
    }

    private fun toDb(power: Double): Double = 10.0 * log10(power + 1e-18)
}
