package com.rotatsulabs.specty.ui.spectrogram

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.rotatsulabs.specty.audio.Spectrogram

/** Turns quantised spectrogram bytes into a bitmap using a black-blue-purple-red-orange-yellow ramp. */
object SpectrogramRenderer {
    private val stops: List<Pair<Float, Int>> = listOf(
        0.00f to rgb(0, 0, 10),
        0.12f to rgb(25, 10, 120),
        0.25f to rgb(110, 20, 150),
        0.40f to rgb(200, 30, 110),
        0.55f to rgb(235, 50, 30),
        0.70f to rgb(250, 130, 20),
        0.85f to rgb(255, 215, 40),
        1.00f to rgb(255, 255, 210),
    )

    /** 256-entry ARGB lookup table. */
    val lut: IntArray by lazy { IntArray(256) { colorAt(it / 255f) } }

    fun gradientColors(count: Int = 16): List<Color> =
        List(count) { Color(lut[it * 255 / (count - 1)]) }

    /** Row 0 is the highest frequency so the image reads bottom-to-top like a normal spectrogram. */
    fun render(spectrogram: Spectrogram, view: Int): Bitmap {
        val columns = spectrogram.columns
        val bins = spectrogram.bins
        val data = spectrogram.views[view]
        val pixels = IntArray(columns * bins)
        for (col in 0 until columns) {
            val base = col * bins
            for (k in 0 until bins) {
                pixels[(bins - 1 - k) * columns + col] = lut[data[base + k].toInt() and 0xFF]
            }
        }
        return Bitmap.createBitmap(pixels, columns, bins, Bitmap.Config.ARGB_8888)
    }

    private fun colorAt(t: Float): Int {
        for (i in 1 until stops.size) {
            val (t1, c1) = stops[i]
            if (t <= t1) {
                val (t0, c0) = stops[i - 1]
                val f = ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
                return lerp(c0, c1, f)
            }
        }
        return stops.last().second
    }

    private fun lerp(a: Int, b: Int, f: Float): Int {
        val r = ((a shr 16 and 0xFF) + ((b shr 16 and 0xFF) - (a shr 16 and 0xFF)) * f).toInt()
        val g = ((a shr 8 and 0xFF) + ((b shr 8 and 0xFF) - (a shr 8 and 0xFF)) * f).toInt()
        val bl = ((a and 0xFF) + ((b and 0xFF) - (a and 0xFF)) * f).toInt()
        return rgb(r, g, bl)
    }

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
