package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpsampleEdgeTest {
    @Test
    fun edgesJustBelowAFortyFourOneOrFortyEightNyquistPointToACdRateSource() {
        for (edge in listOf(21_400.0, 22_000.0, 23_300.0, 23_900.0)) {
            val source = UpsampleEdge.match(edge, 96_000)
            assertNotNull("$edge", source)
            assertFalse("$edge", source!!.hiRes)
            assertEquals("44.1", source.lowKhz)
            assertEquals("48", source.highKhz)
        }
    }

    @Test
    fun anEdgeNearFortySixKilohertzInAOneNinetyTwoFileIsAHighResolutionSource() {
        val source = UpsampleEdge.match(46_300.0, 192_000)
        assertTrue(source!!.hiRes)
        assertEquals("88.2", source.lowKhz)
        assertEquals("96", source.highKhz)
    }

    @Test
    fun edgesThatAreNotAtAKnownNyquistOrHaveNoRoomAboveAreNotMatched() {
        assertNull(UpsampleEdge.match(19_000.0, 96_000))
        assertNull(UpsampleEdge.match(25_100.0, 96_000))
        assertNull(UpsampleEdge.match(46_300.0, 96_000))
        assertNull(UpsampleEdge.match(30_000.0, 192_000))
    }


    private val sixteenBitFloor = Math.pow(2.0, -15.0).let { it * it / 12.0 }

    private fun assess(rate: Int, edge: Double, floorVariance: Double): AuthenticityReport {
        val fft = AnalysisVersion.AUTH_FFT
        val binHz = rate.toDouble() / fft
        val whole = FloatArray(fft / 2) {
            val f = it * binHz
            ((if (f < 8_000.0) -40.0 else -60.0) - if (f < edge) 0.0 else 80.0).toFloat()
        }
        val info = AudioInfo("t", "flac", null, rate, 2, 24, "s32", null, 30.0, 1000)
        return AuthenticityAnalyzer.assess(info, AuthSpectra(rate, fft, whole, List(12) { whole }, floorVariance), 24)
    }

    @Test
    fun aNinetySixKilohertzSourceBehindAOneNinetyTwoFileIsUpsampledEvenWithADeepFloor() {
        val r = assess(192_000, 46_300.0, sixteenBitFloor / 10.0)
        assertEquals(Verdict.UPSAMPLED, r.verdict)
        val note = r.notes.first { it.key == NoteKey.UPSAMPLED }
        assertEquals(listOf("88.2", "96"), note.args.drop(2))
    }

    @Test
    fun aCdRateEdgeWithADeepFloorIsStillWithdrawn() {
        assertEquals(Verdict.BAND_LIMITED, assess(96_000, 21_400.0, sixteenBitFloor / 10.0).verdict)
    }

    @Test
    fun aFortyEightKilohertzSourceEdgeIsMatchedWithASixteenBitFloor() {
        val r = assess(96_000, 23_300.0, sixteenBitFloor * 2)
        assertEquals(Verdict.UPSAMPLED, r.verdict)
        assertEquals(listOf("44.1", "48"), r.notes.first { it.key == NoteKey.UPSAMPLED }.args.drop(2))
    }
}
