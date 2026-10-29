package com.rotatsulabs.specty.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Inter-sample (true) peak estimate for ONE channel using windowed-sinc oversampling
 * (4x below 96 kHz, 2x above).
 *
 * Interpolation phase 0 reproduces the original samples exactly, so the result is never lower
 * than the sample peak. An exact L1-norm bound skips the filters whenever the local window cannot
 * beat the current peak. Channels are independent, so callers can run one instance per thread.
 */
class TruePeakChannel(sampleRate: Int) {
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

    private val history = DoubleArray(taps - 1)
    private var work = DoubleArray(0)

    /** Linear true peak seen so far (full scale = 1.0). */
    var peak: Double = 0.0
        private set

    /** Processes [frames] interleaved frames, reading only [channel] out of [channels]. */
    fun process(samples: FloatArray, frames: Int, channels: Int, channel: Int) {
        val lead = taps - 1
        val end = lead + frames
        if (work.size < end) work = DoubleArray(end)
        val x = work
        System.arraycopy(history, 0, x, 0, lead)
        var idx = channel
        for (i in lead until end) {
            x[i] = samples[idx].toDouble()
            idx += channels
        }

        // Running maximum of the current window, rescanned only when the maximum slides out.
        var maxVal = 0.0
        var maxIdx = -1
        for (i in 0 until lead) {
            val a = abs(x[i])
            if (a >= maxVal) { maxVal = a; maxIdx = i }
        }

        var p = peak
        val bound = l1Bound
        for (i in lead until end) {
            val a = abs(x[i])
            if (a > p) p = a
            if (a >= maxVal) {
                maxVal = a
                maxIdx = i
            } else if (maxIdx < i - lead) {
                maxVal = 0.0
                for (j in i - lead..i) {
                    val v = abs(x[j])
                    if (v >= maxVal) { maxVal = v; maxIdx = j }
                }
            }
            if (maxVal * bound <= p) continue

            val start = i - lead
            for (phase in coefficients) {
                var acc = 0.0
                for (j in 0 until taps) acc += phase[j] * x[start + j]
                val av = abs(acc)
                if (av > p) p = av
            }
        }
        peak = p
        System.arraycopy(x, end - lead, history, 0, lead)
    }

    private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)

    private fun hann(d: Double): Double = if (abs(d) >= half) 0.0 else 0.5 * (1.0 + cos(PI * d / half))
}

/** All-channel convenience wrapper around [TruePeakChannel], processing channels one after another. */
class TruePeakMeter(private val channels: Int, sampleRate: Int) {
    private val perChannel = Array(channels) { TruePeakChannel(sampleRate) }

    val peak: Double get() = perChannel.fold(0.0) { m, c -> max(m, c.peak) }

    fun process(samples: FloatArray, frames: Int) {
        for (c in 0 until channels) perChannel[c].process(samples, frames, channels, c)
    }
}
