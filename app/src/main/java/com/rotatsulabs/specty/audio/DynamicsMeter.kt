package com.rotatsulabs.specty.audio

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Streaming measurements that need only one pass over the samples:
 *  - the standard "DR" meter value (3 s blocks, RMS of the loudest 20% against the second-highest block peak),
 *  - DC offset per channel,
 *  - stereo correlation and left/right balance.
 */
class DynamicsMeter(private val channels: Int, sampleRate: Int) {
    private val blockFrames = (3.0 * sampleRate).toInt().coerceAtLeast(1)
    private var blockFill = 0
    private val blockSquares = DoubleArray(channels)
    private val blockPeaks = DoubleArray(channels)
    private val rmsSquares = Array(channels) { ArrayList<Double>() }
    private val peaksPerBlock = Array(channels) { ArrayList<Double>() }

    private val sums = DoubleArray(channels)
    private val squares = DoubleArray(channels)
    private var crossLR = 0.0
    private var frames = 0L

    fun process(samples: FloatArray, n: Int) {
        for (f in 0 until n) {
            val base = f * channels
            for (c in 0 until channels) {
                val x = samples[base + c].toDouble()
                val a = abs(x)
                sums[c] += x
                squares[c] += x * x
                blockSquares[c] += x * x
                if (a > blockPeaks[c]) blockPeaks[c] = a
            }
            if (channels >= 2) crossLR += samples[base].toDouble() * samples[base + 1].toDouble()
            if (++blockFill == blockFrames) closeBlock()
        }
        frames += n
    }

    private fun closeBlock() {
        for (c in 0 until channels) {
            // The meter's RMS carries +3 dB so a full-scale sine reads 0 dB.
            rmsSquares[c].add(2.0 * blockSquares[c] / blockFill)
            peaksPerBlock[c].add(blockPeaks[c])
            blockSquares[c] = 0.0
            blockPeaks[c] = 0.0
        }
        blockFill = 0
    }

    /** DR in dB (unrounded), or null when the signal is silent. */
    fun dynamicRange(): Double? {
        // A trailing block under a second would skew the loudest-20% statistic, unless it is all we have.
        val partial = blockFill
        val partialUsable = partial > 0 && (rmsSquares[0].isEmpty() || partial >= blockFrames / 3)
        val perChannel = ArrayList<Double>()
        for (c in 0 until channels) {
            val rms = ArrayList(rmsSquares[c])
            val peaks = ArrayList(peaksPerBlock[c])
            if (partialUsable) {
                rms.add(2.0 * blockSquares[c] / partial)
                peaks.add(blockPeaks[c])
            }
            if (rms.isEmpty()) continue
            rms.sortDescending()
            peaks.sortDescending()
            val take = max(1, ceil(rms.size * 0.2).toInt())
            var top = 0.0
            for (i in 0 until take) top += rms[i]
            val rms20 = sqrt(top / take)
            val p2 = if (peaks.size >= 2) peaks[1] else peaks[0]
            if (rms20 <= 1e-9 || p2 <= 1e-9) continue
            perChannel += 20.0 * log10(p2 / rms20)
        }
        if (perChannel.isEmpty()) return null
        return perChannel.average()
    }

    fun dcOffsets(): List<Double> = List(channels) { if (frames > 0) sums[it] / frames else 0.0 }

    /** Pearson-style correlation of the first two channels; null for mono or silence. */
    fun correlation(): Double? {
        if (channels < 2 || squares[0] <= 0.0 || squares[1] <= 0.0) return null
        return (crossLR / sqrt(squares[0] * squares[1])).coerceIn(-1.0, 1.0)
    }

    /** Left minus right RMS in dB. */
    fun balanceDb(): Double? {
        if (channels < 2 || frames == 0L || squares[0] <= 0.0 || squares[1] <= 0.0) return null
        return 10.0 * log10(squares[0] / squares[1])
    }
}
