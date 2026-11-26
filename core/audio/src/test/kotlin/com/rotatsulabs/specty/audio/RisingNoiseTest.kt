package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class RisingNoiseTest {
    private val fft = AnalysisVersion.AUTH_FFT

    /** A smooth fall of 0.9 dB per kHz up to 25 kHz, then [topSlope] dB per kHz. */
    private fun spectrum(rate: Int, topSlope: Double): FloatArray {
        val binHz = rate.toDouble() / fft
        return FloatArray(fft / 2) {
            val khz = it * binHz / 1000.0
            (-40.0 - 0.9 * minOf(khz, 25.0) + topSlope * (khz - 25.0).coerceAtLeast(0.0)).toFloat()
        }
    }

    @Test
    fun aFloorThatClimbsIsNotFullBand() {
        val d = AuthenticityAnalyzer.detect(spectrum(96_000, 0.8), 96_000, fft)
        assertEquals(0.8, d.topSlope!!, 0.05)
        assertEquals(SpectrumShape.UNCLEAR, d.shape)
    }

    @Test
    fun contentThatFallsOrLiesFlatStaysFullBand() {
        for (slope in listOf(-1.0, -0.4, 0.0, 0.3)) {
            val d = AuthenticityAnalyzer.detect(spectrum(96_000, slope), 96_000, fft)
            assertEquals("$slope dB/kHz", SpectrumShape.FULL_BAND, d.shape)
        }
    }

    @Test
    fun filesOfFortyEightKilohertzOrLessAreNotChecked() {
        assertNull(AuthenticityAnalyzer.detect(spectrum(48_000, 0.8), 48_000, fft).topSlope)
    }

    @Test
    fun theVerdictSaysSoAndPeaksAboveTheFloorDoNotOverrideIt() {
        val rate = 96_000
        val whole = spectrum(rate, 0.8)
        val peak = PeakHold(FloatArray(whole.size) { whole[it] + 20f }, 1200, 12)
        val floor = Math.pow(2.0, -15.0).pow(2) / 12.0 / 10.0
        val info = AudioInfo("t", "flac", null, rate, 2, 24, "s32", null, 30.0, 1000)
        val r = AuthenticityAnalyzer.assess(info, AuthSpectra(rate, fft, whole, List(12) { whole }, floor, peak), 24)
        assertEquals(Verdict.INCONCLUSIVE, r.verdict)
        assertNotNull(r.notes.firstOrNull { it.key == NoteKey.RISING_NOISE })
        assertTrue(r.notes.none { it.key == NoteKey.TOP_AT_PEAKS })
    }
}
