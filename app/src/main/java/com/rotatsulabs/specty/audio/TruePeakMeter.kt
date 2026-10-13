package com.rotatsulabs.specty.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Inter-sample (true) peak estimate using windowed-sinc oversampling (4x below 96 kHz, 2x above).
 *
 * The interpolation phase 0 reproduces the original samples exactly, so the result is never lower
 * than the sample peak. An exact L1-norm bound lets us skip the filter when it cannot beat the
 * current peak, which keeps this cheap on normal material.
 */
class TruePeakMeter(private val channels: Int, sampleRate: Int) {
    private val factor = if (sampleRate < 96_000) 4 else 2
    private val half = 12
    private val taps = 2 * half

    /** Coefficients for phases 1 until [factor]; each has [taps] entries. */
    private val coefficients: Array<DoubleArray> = Array(factor - 1) { p ->
        val fraction = (p + 1).toDouble() / factor
        val raw = DoubleArray(taps) { jj ->
            val j = jj - half + 1
            val d = j - fraction
            sinc(d) * hann(d)
        }
        // Unity DC gain per phase, so a truncated window does not inflate the peak.
        val gain = raw.sum()
        DoubleArray(taps) { raw[it] / gain }
    }
    private val l1Bound: Double = coefficients.maxOf { c -> c.sumOf { abs(it) } }

    private val windows = Array(channels) { DoubleArray(taps) }

    /** Linear true peak seen so far (full scale = 1.0). */
    var peak: Double = 0.0
        private set

    fun process(samples: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            val base = f * channels
            for (c in 0 until channels) {
                val w = windows[c]
                System.arraycopy(w, 1, w, 0, taps - 1)
                val s = samples[base + c].toDouble()
                w[taps - 1] = s
                val a = abs(s)
                if (a > peak) peak = a

                var windowMax = 0.0
                for (v in w) {
                    val av = abs(v)
                    if (av > windowMax) windowMax = av
                }
                if (windowMax * l1Bound <= peak) continue

                for (phase in coefficients) {
                    var acc = 0.0
                    for (i in 0 until taps) acc += phase[i] * w[i]
                    val av = abs(acc)
                    if (av > peak) peak = av
                }
            }
        }
    }

    private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

    private fun hann(d: Double): Double = if (abs(d) >= half) 0.0 else 0.5 * (1.0 + cos(PI * d / half))
}
