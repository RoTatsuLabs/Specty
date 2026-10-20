package com.rotatsulabs.specty.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place radix-2 complex FFT. [n] must be a power of two. */
class Fft(val n: Int) {
    private val cosTable = DoubleArray(n / 2)
    private val sinTable = DoubleArray(n / 2)
    private val reversed = IntArray(n)

    init {
        require(n >= 2 && (n and (n - 1)) == 0) { "FFT size must be a power of two, got $n" }
        for (i in 0 until n / 2) {
            val angle = -2.0 * PI * i / n
            cosTable[i] = cos(angle)
            sinTable[i] = sin(angle)
        }
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) {
            reversed[i] = Integer.reverse(i) ushr (32 - bits)
        }
    }

    fun transform(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            if (half < SHORT_BLOCK) {
                // Blocks this small hold only a few butterflies, so take one twiddle at a time and walk it down every
                // block. The same butterflies run, just in another order, so the output is unchanged.
                for (k in 0 until half) {
                    val wr = cosTable[k * step]
                    val wi = sinTable[k * step]
                    var j = k
                    while (j < n) {
                        val l = j + half
                        val tr = re[l] * wr - im[l] * wi
                        val ti = re[l] * wi + im[l] * wr
                        re[l] = re[j] - tr
                        im[l] = im[j] - ti
                        re[j] += tr
                        im[j] += ti
                        j += size
                    }
                }
            } else {
                var start = 0
                while (start < n) {
                    var k = 0
                    for (j in start until start + half) {
                        val wr = cosTable[k]
                        val wi = sinTable[k]
                        val l = j + half
                        val tr = re[l] * wr - im[l] * wi
                        val ti = re[l] * wi + im[l] * wr
                        re[l] = re[j] - tr
                        im[l] = im[j] - ti
                        re[j] += tr
                        im[j] += ti
                        k += step
                    }
                    start += size
                }
            }
            size = size shl 1
        }
    }

    private companion object {
        const val SHORT_BLOCK = 8
    }
}
