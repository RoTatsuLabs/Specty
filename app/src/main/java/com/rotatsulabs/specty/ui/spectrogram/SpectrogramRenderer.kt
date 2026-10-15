package com.rotatsulabs.specty.ui.spectrogram

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.rotatsulabs.specty.audio.FreqScale
import com.rotatsulabs.specty.audio.Spectrogram

/** Colour ramps for the spectrogram; each is a list of (position, RGB) stops from quiet to loud. */
enum class SpectrogramPalette(val label: String, internal val stops: List<Pair<Float, Int>>) {
    Fire(
        "Fire",
        listOf(
            0.00f to rgb(0, 0, 10), 0.12f to rgb(25, 10, 120), 0.25f to rgb(110, 20, 150), 0.40f to rgb(200, 30, 110),
            0.55f to rgb(235, 50, 30), 0.70f to rgb(250, 130, 20), 0.85f to rgb(255, 215, 40), 1.00f to rgb(255, 255, 210),
        ),
    ),
    Inferno(
        "Inferno",
        listOf(
            0.00f to rgb(0, 0, 4), 0.13f to rgb(31, 12, 72), 0.25f to rgb(85, 15, 109), 0.38f to rgb(136, 34, 106),
            0.50f to rgb(186, 54, 85), 0.63f to rgb(227, 89, 51), 0.75f to rgb(249, 140, 10), 0.88f to rgb(249, 201, 50),
            1.00f to rgb(252, 255, 164),
        ),
    ),
    Magma(
        "Magma",
        listOf(
            0.00f to rgb(0, 0, 4), 0.13f to rgb(28, 16, 68), 0.25f to rgb(79, 18, 123), 0.38f to rgb(129, 37, 129),
            0.50f to rgb(181, 54, 122), 0.63f to rgb(229, 80, 100), 0.75f to rgb(251, 135, 97), 0.88f to rgb(254, 194, 135),
            1.00f to rgb(252, 253, 191),
        ),
    ),
    Viridis(
        "Viridis",
        listOf(
            0.00f to rgb(68, 1, 84), 0.13f to rgb(72, 40, 120), 0.25f to rgb(62, 74, 137), 0.38f to rgb(49, 104, 142),
            0.50f to rgb(38, 130, 142), 0.63f to rgb(31, 158, 137), 0.75f to rgb(53, 183, 121), 0.88f to rgb(110, 206, 88),
            1.00f to rgb(253, 231, 37),
        ),
    ),
    Rainbow(
        "Rainbow",
        listOf(
            0.00f to rgb(48, 18, 59), 0.15f to rgb(70, 100, 230), 0.30f to rgb(35, 190, 230), 0.50f to rgb(150, 254, 60),
            0.70f to rgb(250, 186, 57), 0.85f to rgb(228, 73, 13), 1.00f to rgb(122, 4, 3),
        ),
    ),
    Ice(
        "Ice",
        listOf(
            0.00f to rgb(0, 0, 12), 0.25f to rgb(8, 36, 110), 0.50f to rgb(30, 110, 200), 0.75f to rgb(120, 205, 240),
            1.00f to rgb(255, 255, 255),
        ),
    ),
    Grayscale("Grayscale", listOf(0.00f to rgb(0, 0, 0), 1.00f to rgb(255, 255, 255))),
}

private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

/** Turns quantised spectrogram bytes into a bitmap using the chosen palette. */
object SpectrogramRenderer {
    private val luts: Map<SpectrogramPalette, IntArray> by lazy {
        SpectrogramPalette.entries.associateWith { p -> IntArray(256) { colorAt(p.stops, it / 255f) } }
    }

    fun lut(palette: SpectrogramPalette): IntArray = luts.getValue(palette)

    fun gradientColors(palette: SpectrogramPalette, count: Int = 16): List<Color> {
        val table = lut(palette)
        return List(count) { Color(table[it * 255 / (count - 1)]) }
    }

    /** Height of the image when frequency is drawn logarithmically; low rows repeat bins, which is expected. */
    private const val LOG_ROWS = 768

    /**
     * Row 0 is the highest frequency so the image reads bottom-to-top like a normal spectrogram.
     * [options] sets the level window and the frequency scale.
     */
    fun render(spectrogram: Spectrogram, view: Int, palette: SpectrogramPalette, options: SpecOptions = SpecOptions()): Bitmap {
        val table = lut(palette)
        val span = (options.range.ceil - options.range.floor).toDouble()
        val map = IntArray(256) { q ->
            val db = Spectrogram.MIN_DB + q / 255.0 * (Spectrogram.MAX_DB - Spectrogram.MIN_DB)
            val t = ((db - options.range.floor) / span).coerceIn(0.0, 1.0)
            table[(t * 255.0).toInt()]
        }
        val columns = spectrogram.columns
        val bins = spectrogram.bins
        val data = spectrogram.views[view]
        if (!options.log) {
            val pixels = IntArray(columns * bins)
            for (col in 0 until columns) {
                val base = col * bins
                for (k in 0 until bins) {
                    pixels[(bins - 1 - k) * columns + col] = map[data[base + k].toInt() and 0xFF]
                }
            }
            return Bitmap.createBitmap(pixels, columns, bins, Bitmap.Config.ARGB_8888)
        }
        val nyquist = spectrogram.sampleRate / 2.0
        val pixels = IntArray(columns * LOG_ROWS)
        for (y in 0 until LOG_ROWS) {
            val hz = FreqScale.fracToHz(1.0 - (y + 0.5) / LOG_ROWS, nyquist, log = true)
            val bin = Math.round(hz / nyquist * (bins - 1)).toInt().coerceIn(0, bins - 1)
            for (col in 0 until columns) {
                pixels[y * columns + col] = map[data[col * bins + bin].toInt() and 0xFF]
            }
        }
        return Bitmap.createBitmap(pixels, columns, LOG_ROWS, Bitmap.Config.ARGB_8888)
    }

    private fun colorAt(stops: List<Pair<Float, Int>>, t: Float): Int {
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

}
