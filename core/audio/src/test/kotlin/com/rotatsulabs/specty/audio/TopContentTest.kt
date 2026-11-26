package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

class TopContentTest {
    private val rate = 96_000
    private val floorSigma = 0.003

    private fun spectra(bursts: Boolean): AuthSpectra {
        val rnd = Random(7)
        val n = rate * 20
        val bb = ByteBuffer.allocate(n * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val tone = 0.05 * sin(2 * PI * 1000.0 * i / rate) + 0.03 * sin(2 * PI * 5000.0 * i / rate)
            val burst = bursts && (i / rate.toDouble()) % 5.0 < 0.25
            bb.putFloat((tone + rnd.nextGaussian() * floorSigma + if (burst) rnd.nextGaussian() * 0.2 else 0.0).toFloat())
        }
        val file = File.createTempFile("top", ".f32").also { it.deleteOnExit(); it.writeBytes(bb.array()) }
        return PcmFile(file, 1, rate).use { AuthSpectrumAnalyzer.compute(it) }
    }

    @Test
    fun aFloorAloneSitsAtTheLevelItShouldReach() {
        val s = spectra(bursts = false)
        val margin = TopContent.marginDb(s.peak, floorSigma * floorSigma, s.sampleRate, s.fftSize)
        assertNotNull(margin)
        assertEquals(0.0, margin!!, 2.0)
    }

    @Test
    fun briefBurstsLiftThePeakHoldLevelFarAboveTheFloor() {
        val s = spectra(bursts = true)
        val margin = TopContent.marginDb(s.peak, floorSigma * floorSigma, s.sampleRate, s.fftSize)
        assertTrue("margin $margin", margin!! > 20.0)
    }

    @Test
    fun withoutAFloorOrPeaksThereIsNoReading() {
        val s = spectra(bursts = true)
        assertNull(TopContent.marginDb(s.peak, null, s.sampleRate, s.fftSize))
        assertNull(TopContent.marginDb(null, 1e-8, s.sampleRate, s.fftSize))
        assertNull(TopContent.marginDb(s.peak, 1e-8, 44_100, s.fftSize))
    }


    private val bins = AnalysisVersion.AUTH_FFT / 2
    private val binHz = rate.toDouble() / AnalysisVersion.AUTH_FFT
    private val info = AudioInfo("t", "flac", null, rate, 2, 24, "s32", null, 30.0, 1000)
    private val sixteenBitFloor = Math.pow(2.0, -15.0).let { it * it / 12.0 }

    private fun cutAt20k() = FloatArray(bins) { if (it * binHz < 20_000.0) -60f else -140f }

    private fun assess(peakAbove: (Float) -> Float): AuthenticityReport {
        val whole = cutAt20k()
        val peak = PeakHold(FloatArray(bins) { if (it * binHz < TopContent.FROM_HZ) whole[it] else peakAbove(whole[it]) }, 1200, 12)
        val spectra = AuthSpectra(rate, AnalysisVersion.AUTH_FFT, whole, List(12) { whole }, sixteenBitFloor / 10.0, peak)
        return AuthenticityAnalyzer.assess(info, spectra, 24)
    }

    @Test
    fun peaksAboveTheFloorMakeACutAverageReadAsFullBand() {
        val cut = assess { it + 9f }
        assertNotEquals(Verdict.HI_RES, cut.verdict)
        val hit = assess { -75f }
        assertEquals(Verdict.HI_RES, hit.verdict)
        assertEquals(SpectrumShape.FULL_BAND, hit.shape)
        assertEquals(listOf(NoteKey.TOP_AT_PEAKS), hit.notes.map { it.key })
    }
}
