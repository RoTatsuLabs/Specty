package com.rotatsulabs.specty.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The speed-ups in the analysis must not move a single bit of what it measures. */
class ScanSpeedTest {
    /** The transform as it was before the early stages were reordered: one block at a time, twiddles read with a stride. */
    private class ReferenceFft(val n: Int) {
        private val cosTable = DoubleArray(n / 2) { cos(-2.0 * PI * it / n) }
        private val sinTable = DoubleArray(n / 2) { sin(-2.0 * PI * it / n) }

        fun transform(re: DoubleArray, im: DoubleArray) {
            val bits = Integer.numberOfTrailingZeros(n)
            for (i in 0 until n) {
                val j = Integer.reverse(i) ushr (32 - bits)
                if (j > i) {
                    val tr = re[i]; re[i] = re[j]; re[j] = tr
                    val ti = im[i]; im[i] = im[j]; im[j] = ti
                }
            }
            var size = 2
            while (size <= n) {
                val half = size / 2
                val step = n / size
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
                size = size shl 1
            }
        }
    }

    @Test
    fun fftGivesTheSameBitsAsTheOriginalLoopOrder() {
        val random = Random(7)
        for (n in listOf(2, 4, 8, 16, 64, 256, 2048, 4096)) {
            val input = DoubleArray(n) { random.nextGaussian() }
            val a = input.copyOf(); val ai = DoubleArray(n)
            val b = input.copyOf(); val bi = DoubleArray(n)
            Fft(n).transform(a, ai)
            ReferenceFft(n).transform(b, bi)
            assertArrayEquals("real part, n=$n", b, a, 0.0)
            assertArrayEquals("imaginary part, n=$n", bi, ai, 0.0)
        }
    }

    private fun surround(channels: Int, frames: Int): File {
        val file = File.createTempFile("surround", ".f32").apply { deleteOnExit() }
        val bytes = ByteBuffer.allocate(frames * channels * 4).order(ByteOrder.LITTLE_ENDIAN)
        val random = Random(3)
        for (f in 0 until frames) for (c in 0 until channels) {
            // Each channel gets its own tone and level, so a mix-up between channels would show in the peak.
            bytes.putFloat((0.1f * (c + 1) * sin(2 * PI * (200.0 + 450.0 * c) * f / 48_000).toFloat() + 0.01f * random.nextGaussian().toFloat()))
        }
        file.writeBytes(bytes.array())
        return file
    }

    @Test
    fun trueAndSamplePeaksDoNotDependOnHowManyWorkersShareTheChannels() {
        val file = surround(6, 48_000 * 3)
        val results = listOf(1, 2, 4, 6, 8).map { threads ->
            PcmFile(file, 6, 48_000).use { LevelAnalyzer.analyze(it, threads) }
        }
        val reference = PcmFile(file, 6, 48_000).use { pcm ->
            val meter = TruePeakMeter(6, 48_000)
            pcm.forEachChunk { buf, n -> meter.process(buf, n) }
            meter.peak
        }
        for (r in results) {
            assertEquals(LevelAnalyzer.toDb(reference), r.truePeakDbtp, 0.0)
            assertEquals(results[0].peakDb, r.peakDb, 0.0)
            assertEquals(results[0].lufs, r.lufs)
            assertEquals(results[0].drTt, r.drTt)
        }
    }
}
