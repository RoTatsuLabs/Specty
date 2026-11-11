package com.rotatsulabs.specty.ui.spectrogram

import com.rotatsulabs.specty.audio.Fft
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.audio.WindowFn
import java.util.Random
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A short synthetic signal run through the same kind of transform as a real analysis, so the settings page can show how
 * a window size and function change the picture. Rising harmonics, a few clicks and a quiet noise floor, with nothing above 16 kHz.
 */
object SpectrogramSample {
    private const val RATE = 44_100
    private const val SECONDS = 3.0
    private const val COLUMNS = 160

    fun build(fftSize: Int, window: WindowFn): Spectrogram {
        val total = (RATE * SECONDS).toInt()
        val signal = DoubleArray(total)
        val random = Random(7)
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / total
            val f0 = 180.0 + 520.0 * t * t
            phase += 2.0 * PI * f0 / RATE
            var v = 0.0
            var k = 1
            while (k <= 40 && f0 * k < 16_000.0) {
                v += sin(phase * k) / (k * 1.4)
                k++
            }
            signal[i] = 0.18 * v + 0.0004 * (random.nextDouble() - 0.5)
        }
        // Clicks: short decaying bursts of a high tone, one every half second.
        for (c in 1..5) {
            val start = (c * 0.5 * RATE).toInt()
            for (j in 0 until 900) {
                val i = start + j
                if (i >= total) break
                val decay = Math.exp(-j / 150.0)
                signal[i] += 0.35 * decay * sin(2.0 * PI * 9_000.0 * j / RATE)
            }
        }

        val bins = fftSize / 2
        val w = window.coefficients(fftSize)
        val norm = w.sum()
        val fft = Fft(fftSize)
        val re = DoubleArray(fftSize)
        val im = DoubleArray(fftSize)
        val hop = max(1, (total - fftSize) / (COLUMNS - 1))
        val data = ByteArray(COLUMNS * bins)
        for (col in 0 until COLUMNS) {
            val from = col * hop
            for (n in 0 until fftSize) {
                re[n] = (signal.getOrElse(from + n) { 0.0 }) * w[n]
                im[n] = 0.0
            }
            fft.transform(re, im)
            for (k in 0 until bins) {
                val mag = 2.0 * sqrt(re[k] * re[k] + im[k] * im[k]) / norm
                val db = 20.0 * log10(max(mag, 1e-9))
                val q = ((db - Spectrogram.MIN_DB) / (Spectrogram.MAX_DB - Spectrogram.MIN_DB) * 255.0).toInt().coerceIn(0, 255)
                data[col * bins + k] = q.toByte()
            }
        }
        return Spectrogram(COLUMNS, bins, RATE, fftSize, SECONDS, listOf(data), listOf("Mix"))
    }
}
