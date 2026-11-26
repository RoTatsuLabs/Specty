package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class HiResTest {
    private val fft = AnalysisVersion.AUTH_FFT
    private val sixteenBit = 2.0.pow(-15).let { it * it / 12.0 }

    private fun assess(
        rate: Int = 96_000,
        bits: Int? = 24,
        floorDb: Double? = -10.0,
        effectiveBits: Int? = 24,
        edgeHz: Double? = null,
    ): AuthenticityReport {
        val binHz = rate.toDouble() / fft
        val whole = FloatArray(fft / 2) {
            val f = it * binHz
            // A smooth fall, so a full-band file has no edge; or the same with an 80 dB cliff at the edge.
            (-40.0 - 0.7 * f / 1000.0 - if (edgeHz != null && f >= edgeHz) 80.0 else 0.0).toFloat()
        }
        val floor = floorDb?.let { sixteenBit * 10.0.pow(it / 10.0) }
        val info = AudioInfo("t", "flac", null, rate, 2, bits, null, null, 30.0, 1000)
        return AuthenticityAnalyzer.assess(info, AuthSpectra(rate, fft, whole, List(12) { whole }, floor), effectiveBits)
    }

    @Test
    fun fullBandContentOverADeepFloorIsVerified() {
        val r = assess(floorDb = -10.0)
        assertEquals(Verdict.HI_RES, r.verdict)
        assertEquals(HiRes.VERIFIED, r.hiRes)
    }

    @Test
    fun aSixteenBitFloorBesideFullBandContentIsUnconfirmed() {
        val r = assess(floorDb = 5.0)
        assertEquals(Verdict.GENUINE, r.verdict)
        assertEquals(HiRes.UNCONFIRMED, r.hiRes)
    }

    @Test
    fun aFloorOfTenDecibelsOrMoreIsAnalogNoiseAndStaysVerified() {
        for (db in listOf(10.0, 15.0, 20.0)) assertEquals("+$db dB", HiRes.VERIFIED, assess(floorDb = db).hiRes)
    }

    @Test
    fun sixteenBitSamplesAreNotHiResWhateverTheRate() {
        val r = assess(bits = 16, effectiveBits = 16, floorDb = 8.0)
        assertEquals(Verdict.GENUINE, r.verdict)
        assertEquals(HiRes.NOT_HI_RES, r.hiRes)
        assertTrue(r.notes.any { it.key == NoteKey.SIXTEEN_BIT_FILE })
    }

    @Test
    fun sixteenBitDataInAWiderFileIsNotHiRes() {
        assertEquals(HiRes.NOT_HI_RES, assess(bits = 24, effectiveBits = 16, floorDb = -10.0).hiRes)
    }

    @Test
    fun filesOfFortyEightKilohertzOrLessHaveNoHiResState() {
        assertNull(assess(rate = 48_000).hiRes)
        assertNull(assess(rate = 44_100).hiRes)
    }

    @Test
    fun anEdgeWithNothingAboveItIsDecidedByTheFloor() {
        val deep = assess(edgeHz = 30_000.0, floorDb = -10.0)
        assertEquals(HiRes.VERIFIED, deep.hiRes)
        assertTrue(deep.notes.any { it.key == NoteKey.FLOOR_BELOW_16BIT })
        val cd = assess(edgeHz = 30_000.0, floorDb = 3.0)
        assertEquals(HiRes.NOT_HI_RES, cd.hiRes)
        assertEquals(HiRes.UNCONFIRMED, assess(edgeHz = 30_000.0, floorDb = 15.0).hiRes)
        assertEquals(HiRes.UNCONFIRMED, assess(edgeHz = 30_000.0, floorDb = null).hiRes)
    }

    @Test
    fun anUpsampledCdRateSourceIsNotHiResAndAnUpsampledNinetySixIsVerified() {
        assertEquals(HiRes.NOT_HI_RES, assess(edgeHz = 21_400.0, floorDb = 3.0).hiRes)
        assertEquals(HiRes.VERIFIED, assess(rate = 192_000, edgeHz = 46_300.0, floorDb = -10.0).hiRes)
    }
}
