package com.rotatsulabs.specty.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Builds a quantised STFT spectrogram plus a long-term average spectrum for cutoff detection. */
object SpectrogramAnalyzer {
    class Output(val spectrogram: Spectrogram, val averageDb: FloatArray)

    /** Frames quieter than this (dBFS, summed power) are ignored when averaging the spectrum. */
    private const val ACTIVE_FRAME_DB = -70.0

    fun analyze(
        pcm: PcmFile,
        fftSize: Int,
        maxColumns: Int = 1600,
        onProgress: (Float) -> Unit = {},
    ): Output {
        val channels = pcm.channels
        val bins = fftSize / 2
        val total = pcm.frames
        val columns = if (total <= fftSize) {
            1
        } else {
            min(maxColumns.toLong(), (total - fftSize) / (fftSize / 2) + 1).toInt()
        }

        val fft = Fft(fftSize)
        val window = DoubleArray(fftSize) { 0.5 - 0.5 * cos(2.0 * PI * it / fftSize) }
        val windowSum = window.sum()

        val viewCount = if (channels == 1) 1 else channels + 1
        val views = List(viewCount) { ByteArray(columns * bins) }
        val buffer = FloatArray(fftSize * channels)
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        val mixPower = DoubleArray(bins)

        val activeSum = DoubleArray(bins)
        var activeCount = 0
        val allSum = DoubleArray(bins)

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
                val view = if (channels == 1) 0 else c + 1
                val target = views[view]
                for (k in 0 until bins) {
                    val magnitude = sqrt(re[k] * re[k] + im[k] * im[k]) * 2.0 / windowSum
                    val power = magnitude * magnitude
                    mixPower[k] += power
                    target[col * bins + k] = quantize(power)
                }
            }

            var framePower = 0.0
            for (k in 0 until bins) {
                val power = mixPower[k] / channels
                framePower += power
                if (channels > 1) views[0][col * bins + k] = quantize(power)
                allSum[k] += toDb(power)
            }
            if (toDb(framePower) > ACTIVE_FRAME_DB) {
                for (k in 0 until bins) activeSum[k] += toDb(mixPower[k] / channels)
                activeCount++
            }

            if (col % 16 == 0) onProgress(col.toFloat() / columns)
        }

        val average = FloatArray(bins)
        if (activeCount > 0) {
            for (k in 0 until bins) average[k] = (activeSum[k] / activeCount).toFloat()
        } else {
            for (k in 0 until bins) average[k] = (allSum[k] / columns).toFloat()
        }

        val labels = if (channels == 1) {
            listOf("Mono")
        } else {
            listOf("Channels") + List(channels) { "Ch ${it + 1}" }
        }
        val spectrogram = Spectrogram(
            columns = columns,
            bins = bins,
            sampleRate = pcm.sampleRate,
            fftSize = fftSize,
            durationSec = total.toDouble() / pcm.sampleRate,
            views = views,
            viewLabels = labels,
        )
        return Output(spectrogram, average)
    }

    private fun toDb(power: Double): Double = 10.0 * log10(power + 1e-18)

    private fun quantize(power: Double): Byte {
        val t = (toDb(power) - Spectrogram.MIN_DB) / (Spectrogram.MAX_DB - Spectrogram.MIN_DB)
        return (t.coerceIn(0.0, 1.0) * 255.0).roundToInt().toByte()
    }
}
