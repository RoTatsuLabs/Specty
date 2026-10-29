package com.rotatsulabs.specty.audio

import kotlin.math.PI
import kotlin.math.cos

/** Analysis window for the spectrogram. Tighter side lobes show weak content next to loud tones more cleanly. */
enum class WindowFn(val label: String) {
    Hann("Hann"),
    Hamming("Hamming"),
    BlackmanHarris("Blackman-Harris");

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
