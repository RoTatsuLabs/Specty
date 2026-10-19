package com.rotatsulabs.specty.audio

import androidx.annotation.StringRes
import com.rotatsulabs.specty.R
import kotlin.math.PI
import kotlin.math.cos

/** Analysis window for the spectrogram. Tighter side lobes show weak content next to loud tones more cleanly. */
enum class WindowFn(val label: String, @StringRes val noteRes: Int) {
    Hann("Hann", R.string.window_hann_note),
    Hamming("Hamming", R.string.window_hamming_note),
    BlackmanHarris("Blackman-Harris", R.string.window_blackman_harris_note);

    /** Periodic window of [n] samples. */
    fun coefficients(n: Int): DoubleArray = DoubleArray(n) { i ->
        val x = 2.0 * PI * i / n
        when (this) {
            Hann -> 0.5 - 0.5 * cos(x)
            Hamming -> 0.54 - 0.46 * cos(x)
            BlackmanHarris -> 0.35875 - 0.48829 * cos(x) + 0.14128 * cos(2 * x) - 0.01168 * cos(3 * x)
        }
    }
}
