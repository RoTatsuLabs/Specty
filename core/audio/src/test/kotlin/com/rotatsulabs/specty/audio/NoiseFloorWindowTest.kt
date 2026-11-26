package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

class NoiseFloorWindowTest {
    private val sixteenBit = 2.0.pow(-15).let { it * it / 12.0 }
    private fun variance(db: Double) = sixteenBit * 10.0.pow(db / 10.0)

    @Test
    fun floorsFromZeroToTwentyDecibelsAreReadInOrder() {
        val expected = mapOf(
            0.0 to NoiseFloor.Level.AT_16BIT,
            5.0 to NoiseFloor.Level.AT_16BIT,
            10.0 to NoiseFloor.Level.MASKED,
            15.0 to NoiseFloor.Level.MASKED,
            20.0 to NoiseFloor.Level.MASKED,
        )
        for ((db, level) in expected) assertEquals("+$db dB", level, NoiseFloor.classify(variance(db))!!.level)
    }

    @Test
    fun theEdgesOfTheWindowAreMinusSixAndNinePointFive() {
        assertEquals(NoiseFloor.Level.BELOW_16BIT, NoiseFloor.classify(variance(-6.5))!!.level)
        assertEquals(NoiseFloor.Level.AT_16BIT, NoiseFloor.classify(variance(-5.5))!!.level)
        assertEquals(NoiseFloor.Level.AT_16BIT, NoiseFloor.classify(variance(9.0))!!.level)
        assertEquals(NoiseFloor.Level.MASKED, NoiseFloor.classify(variance(10.0))!!.level)
    }

    @Test
    fun noiseThatStopsAtALowerSourceNyquistIsReadAgainstItsOwnBand() {
        // 16-bit dither of a 44.1 kHz source inside a 96 kHz file reads 3.4 dB high when spread over the whole band.
        val share = 22_050.0 / 48_000.0
        val spread = variance(7.0) / share
        assertEquals(NoiseFloor.Level.MASKED, NoiseFloor.classify(spread)!!.level)
        assertEquals(NoiseFloor.Level.AT_16BIT, NoiseFloor.classify(spread, share)!!.level)
    }


    private fun assess(floorDb: Double): AuthenticityReport {
        val rate = 96_000
        val fft = AnalysisVersion.AUTH_FFT
        val binHz = rate.toDouble() / fft
        val whole = FloatArray(fft / 2) { -40f - (it * binHz / 1000.0).toFloat() * 0.7f }
        val info = AudioInfo("t", "flac", null, rate, 2, 24, "s32", null, 30.0, 1000)
        return AuthenticityAnalyzer.assess(info, AuthSpectra(rate, fft, whole, List(12) { whole }, variance(floorDb)), 24)
    }

    @Test
    fun aFullBandFileKeepsHiResExceptWhenItsFloorIsThatOfSixteenBitAudio() {
        assertEquals(Verdict.GENUINE, assess(0.0).verdict)
        assertEquals(Verdict.GENUINE, assess(5.0).verdict)
        for (db in listOf(10.0, 15.0, 20.0)) assertEquals("+$db dB", Verdict.HI_RES, assess(db).verdict)
    }
}
