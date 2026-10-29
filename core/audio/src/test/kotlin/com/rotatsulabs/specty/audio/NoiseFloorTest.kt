package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** The noise floor of quiet passages against 16-bit quantisation noise, from the spectra the verdict is judged on. */
class NoiseFloorTest {
    private val lsbVariance = (2.0.pow(-15)).pow(2) / 12.0

    @Test
    fun classifiesAgainstSixteenBitNoise() {
        assertEquals(NoiseFloor.Level.AT_16BIT, NoiseFloor.classify(lsbVariance)!!.level)
        assertEquals(NoiseFloor.Level.AT_16BIT, NoiseFloor.classify(lsbVariance * 2)!!.level)
        assertEquals(NoiseFloor.Level.BELOW_16BIT, NoiseFloor.classify(lsbVariance / 10)!!.level)
        assertEquals(NoiseFloor.Level.MASKED, NoiseFloor.classify(lsbVariance * 1000)!!.level)
        assertNull(NoiseFloor.classify(null))
        assertNull(NoiseFloor.classify(Double.NaN))
        assertNull(NoiseFloor.classify(0.0))
    }

    private fun spectra(bitsStep: Double, seed: Long, dither: Boolean): AuthSpectra {
        val rnd = Random(seed)
        val n = 44_100 * 8
        val bb = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            // A quiet tone, then quantised to the step; TPDF dither as a mastering chain would add it.
            val tone = 0.001 * sin(2 * PI * 1000.0 * i / 44_100.0)
            val d = if (dither) (rnd.nextDouble() - rnd.nextDouble()) * bitsStep else 0.0
            val q = Math.round((tone + d) / bitsStep) * bitsStep
            bb.putFloat(q.toFloat())
        }
        val file = File.createTempFile("floor", ".f32").also { it.deleteOnExit(); it.writeBytes(bb.array()) }
        return PcmFile(file, 1, 44_100).use { AuthSpectrumAnalyzer.compute(it) }
    }

    @Test
    fun sixteenBitAudioReadsAtSixteenBits() {
        val reading = NoiseFloor.classify(spectra(2.0.pow(-15), 1, dither = true).quietFloorVariance)
        assertNotNull(reading)
        assertEquals(NoiseFloor.Level.AT_16BIT, reading!!.level)
    }

    @Test
    fun twentyFourBitAudioReadsBelowSixteenBits() {
        val reading = NoiseFloor.classify(spectra(2.0.pow(-23), 2, dither = true).quietFloorVariance)
        assertNotNull(reading)
        assertEquals(NoiseFloor.Level.BELOW_16BIT, reading!!.level)
    }
}
