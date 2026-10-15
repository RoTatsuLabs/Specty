package com.rotatsulabs.specty.audio

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Maps between a vertical position (0 at the bottom, 1 at the top) and a frequency, linearly or logarithmically.
 * The log scale starts at [LOG_MIN_HZ] because log(0) is undefined.
 */
object FreqScale {
    const val LOG_MIN_HZ = 30.0

    fun fracToHz(frac: Double, nyquist: Double, log: Boolean): Double =
        if (!log) frac * nyquist else LOG_MIN_HZ * (nyquist / LOG_MIN_HZ).pow(frac)

    fun hzToFrac(hz: Double, nyquist: Double, log: Boolean): Double =
        if (!log) (hz / nyquist).coerceIn(0.0, 1.0)
        else (ln(max(hz, LOG_MIN_HZ) / LOG_MIN_HZ) / ln(nyquist / LOG_MIN_HZ)).coerceIn(0.0, 1.0)

    private val LOG_TICKS = listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0)

    /** Tick frequencies for a fixed-size plot: whole kHz steps when linear, 1-2-5 decades when logarithmic. */
    fun ticks(nyquist: Double, log: Boolean): List<Double> {
        if (log) return LOG_TICKS.filter { it < nyquist * 0.999 }
        val k = nyquist / 1000.0
        val step = when {
            k > 30.0 -> 10
            k > 12.0 -> 5
            k > 6.0 -> 2
            else -> 1
        }
        return generateSequence(0) { it + step }.takeWhile { it <= k }.map { it * 1000.0 }.toList()
    }

    fun label(hz: Double): String = when {
        hz == 0.0 -> "0"
        hz < 1000.0 -> hz.toInt().toString()
        hz % 1000.0 == 0.0 -> "${(hz / 1000).toInt()}k"
        else -> String.format(java.util.Locale.US, "%.1fk", hz / 1000.0)
    }
}
