package com.rotatsulabs.specty.audio

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Integrated loudness per ITU-R BS.1770-4 (K-weighting, 400 ms blocks with 75% overlap,
 * -70 LUFS absolute gate and -10 LU relative gate).
 */
class LoudnessMeter(private val sampleRate: Int, private val channels: Int) {

    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        private var z1 = 0.0
        private var z2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }
    }

    companion object {
        private const val SHELF_FREQ = 1681.974450955533
        private const val SHELF_GAIN_DB = 3.999843853973347
        private const val SHELF_Q = 0.7071752369554196
        private const val HP_FREQ = 38.13547087602444
        private const val HP_Q = 0.5003270373238773

        private fun shelf(fs: Double): Biquad {
            val k = tan(PI * SHELF_FREQ / fs)
            val vh = 10.0.pow(SHELF_GAIN_DB / 20.0)
            val vb = vh.pow(0.4996667741545416)
            val a0 = 1.0 + k / SHELF_Q + k * k
            return Biquad(
                b0 = (vh + vb * k / SHELF_Q + k * k) / a0,
                b1 = 2.0 * (k * k - vh) / a0,
                b2 = (vh - vb * k / SHELF_Q + k * k) / a0,
                a1 = 2.0 * (k * k - 1.0) / a0,
                a2 = (1.0 - k / SHELF_Q + k * k) / a0,
            )
        }

        private fun highPass(fs: Double): Biquad {
            val k = tan(PI * HP_FREQ / fs)
            val a0 = 1.0 + k / HP_Q + k * k
            return Biquad(
                b0 = 1.0,
                b1 = -2.0,
                b2 = 1.0,
                a1 = 2.0 * (k * k - 1.0) / a0,
                a2 = (1.0 - k / HP_Q + k * k) / a0,
            )
        }
    }

    private val shelves = Array(channels) { shelf(sampleRate.toDouble()) }
    private val highPasses = Array(channels) { highPass(sampleRate.toDouble()) }

    /** BS.1770 channel weights; for 5.1 the LFE (index 3) is excluded and surrounds get +1.5 dB. */
    private val weights = DoubleArray(channels) { i ->
        when {
            channels >= 6 && i == 3 -> 0.0
            channels >= 6 && (i == 4 || i == 5) -> 1.41
            else -> 1.0
        }
    }

    private val hopFrames = (0.1 * sampleRate).roundToInt().coerceAtLeast(1)
    private val sub = DoubleArray(channels)
    private var subCount = 0
    private val history = Array(4) { DoubleArray(channels) }
    private var historyIndex = 0
    private var historyFilled = 0

    private var energies = DoubleArray(1024)
    private var blockCount = 0

    fun process(samples: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            val base = f * channels
            for (c in 0 until channels) {
                val x = samples[base + c].toDouble()
                val y = highPasses[c].process(shelves[c].process(x))
                sub[c] += y * y
            }
            subCount++
            if (subCount == hopFrames) finishSubBlock()
        }
    }

    private fun finishSubBlock() {
        val slot = history[historyIndex]
        for (c in 0 until channels) {
            slot[c] = sub[c] / hopFrames
            sub[c] = 0.0
        }
        subCount = 0
        historyIndex = (historyIndex + 1) % 4
        if (historyFilled < 4) historyFilled++
        if (historyFilled == 4) {
            var energy = 0.0
            for (c in 0 until channels) {
                val meanSquare = (history[0][c] + history[1][c] + history[2][c] + history[3][c]) / 4.0
                energy += weights[c] * meanSquare
            }
            if (blockCount == energies.size) energies = energies.copyOf(energies.size * 2)
            energies[blockCount++] = energy
        }
    }

    /** Integrated loudness in LUFS, or null when the signal is too short or entirely gated out. */
    fun integratedLufs(): Double? {
        if (blockCount == 0) return null
        val absoluteGate = 10.0.pow((-70.0 + 0.691) / 10.0)
        var sum = 0.0
        var count = 0
        for (i in 0 until blockCount) {
            if (energies[i] > absoluteGate) {
                sum += energies[i]
                count++
            }
        }
        if (count == 0) return null
        val relativeGate = (sum / count) * 0.1
        var gatedSum = 0.0
        var gatedCount = 0
        for (i in 0 until blockCount) {
            if (energies[i] > absoluteGate && energies[i] > relativeGate) {
                gatedSum += energies[i]
                gatedCount++
            }
        }
        if (gatedCount == 0) return null
        return -0.691 + 10.0 * log10(gatedSum / gatedCount)
    }

    /**
     * Loudness over time as [points] equal slices of [durationSec]; each slice averages the 400 ms
     * blocks that start inside it. Slices without a block repeat the previous value; silence is -70.
     */
    fun series(points: Int, durationSec: Double): FloatArray {
        val sums = DoubleArray(points)
        val counts = IntArray(points)
        for (i in 0 until blockCount) {
            val centre = i * 0.1 + 0.2
            val slice = (centre / durationSec * points).toInt().coerceIn(0, points - 1)
            sums[slice] += energies[i]
            counts[slice]++
        }
        val out = FloatArray(points)
        var last = -70f
        for (i in 0 until points) {
            if (counts[i] > 0) {
                val mean = sums[i] / counts[i]
                last = (if (mean <= 0.0) -70.0 else (-0.691 + 10.0 * log10(mean)).coerceAtLeast(-70.0)).toFloat()
            }
            out[i] = last
        }
        return out
    }
}
