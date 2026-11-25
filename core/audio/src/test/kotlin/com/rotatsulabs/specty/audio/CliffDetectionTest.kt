package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Cliffs of different widths and depths, in spectra shaped by hand (dB per bin, 96 kHz, 4096-point transform). */
class CliffDetectionTest {
    private val rate = 96_000
    private val fft = AnalysisVersion.AUTH_FFT
    private val binHz = rate.toDouble() / fft

    private fun detect(level: (Double) -> Double) =
        AuthenticityAnalyzer.detect(FloatArray(fft / 2) { level(it * binHz).toFloat() }, rate, fft)

    /** -40 dB up to 8 kHz, -60 dB above, then [stepDb] lower over [widthHz] starting at [fromHz], flat again. */
    private fun step(fromHz: Double, widthHz: Double, stepDb: Double): (Double) -> Double = { f ->
        (if (f < 8_000.0) -40.0 else -60.0) - stepDb * ((f - fromHz) / widthHz).coerceIn(0.0, 1.0)
    }

    @Test
    fun aBrickWallIsFoundAtItsEdge() {
        val d = detect(step(20_000.0, 100.0, 80.0))
        assertEquals(20_000.0, d.cutoff!!, 150.0)
        assertEquals(SpectrumShape.ABRUPT_CUTOFF, d.shape)
    }

    @Test
    fun aResamplersTransitionBandOfThreeKilohertzIsFoundAtItsMiddle() {
        val d = detect(step(21_000.0, 3_000.0, 50.0))
        assertNotNull(d.cutoff)
        assertEquals(22_500.0, d.cutoff!!, 700.0)
    }

    @Test
    fun aTwentyFourDecibelStepCountsAndAnEighteenDecibelStepDoesNot() {
        assertEquals(21_000.0, detect(step(21_000.0, 200.0, 24.0)).cutoff!!, 300.0)
        assertNull(detect(step(21_000.0, 200.0, 18.0)).cutoff)
    }

    @Test
    fun aGentleRolloffIsNotACliff() {
        // 5 dB per kHz from 10 kHz down to the top of the band, which reaches the floor without any edge.
        assertNull(detect { f -> -40.0 - 5.0 * ((f - 8_000.0) / 1000.0).coerceAtLeast(0.0) }.cutoff)
    }

    @Test
    fun aDropThatClimbsBackIsNotACliff() {
        val d = detect { f ->
            when {
                f < 8_000.0 -> -40.0
                f < 20_000.0 -> -60.0
                f < 24_000.0 -> -100.0
                else -> -65.0
            }
        }
        assertNull(d.cutoff)
    }
}
