package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Effective bit depth is read from decoded float samples, the way the analyzer sees them. */
class BitDepthTest {
    private fun levels(samples: FloatArray): LevelStats {
        val bb = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { bb.putFloat(it) }
        val file = File.createTempFile("bits", ".f32").also { it.deleteOnExit(); it.writeBytes(bb.array()) }
        return PcmFile(file, 1, 44100).use { LevelAnalyzer.analyze(it) }
    }

    /** 16-bit integers as an f32 decode produces them. */
    private fun sixteen(n: Int, seed: Long, range: Int = 20_000, gain: Double = 1.0, dither: Boolean = false): FloatArray {
        val rnd = Random(seed)
        return FloatArray(n) {
            var k = rnd.nextInt(2 * range + 1) - range
            if (dither) k += rnd.nextInt(2) - rnd.nextInt(2)
            (k * gain / 32768.0).toFloat()
        }
    }

    @Test
    fun sixteenBitInATwentyFourBitFileIsPadded() {
        val l = levels(sixteen(60_000, 1))
        assertEquals(16, l.effectiveBitDepth)
        assertEquals(BitDepthBasis.ZERO_PADDED, l.bitDepthBasis)
        assertTrue(BitDepth.isPadded(24, l.effectiveBitDepth))
    }

    @Test
    fun ditheredSixteenBitIsStillOnTheSixteenBitGrid() {
        assertEquals(16, levels(sixteen(60_000, 2, dither = true)).effectiveBitDepth)
    }

    @Test
    fun oneStrayOddSampleDoesNotHideThePadding() {
        val s = sixteen(60_000, 3)
        s[12_345] = (1234.0 / 8388608.0).toFloat() + 1e-7f
        val l = levels(s)
        assertEquals(16, l.effectiveBitDepth)
    }

    @Test
    fun aGainChangeAfterPaddingLeavesACoarseGrid() {
        // 16-bit audio scaled by 0.7 fills the low bits, but the levels stay sparse.
        val l = levels(sixteen(450_000, 4, range = 250, gain = 0.7))
        assertEquals(BitDepthBasis.LEVEL_GRID, l.bitDepthBasis)
        assertTrue("effective ${l.effectiveBitDepth}", l.effectiveBitDepth!! in 15..18)
    }

    @Test
    fun aQuietTwentyFourBitSignalIsNotMistakenForASparseGrid() {
        val rnd = Random(5)
        val s = FloatArray(700_000) { ((rnd.nextInt(131_073) - 65_536) / 8388608.0).toFloat() }
        val l = levels(s)
        assertEquals(24, l.effectiveBitDepth)
        assertEquals(BitDepthBasis.ALL_BITS, l.bitDepthBasis)
    }

    @Test
    fun fullRangeTwentyFourBitAndFloatSourcesUseAllBits() {
        val rnd = Random(6)
        val int24 = FloatArray(80_000) { ((rnd.nextInt(1 shl 24) - (1 shl 23)) / 8388608.0).toFloat() }
        assertEquals(24, levels(int24).effectiveBitDepth)
        val floats = FloatArray(80_000) { (rnd.nextGaussian() * 0.1).toFloat() }
        val l = levels(floats)
        assertEquals(24, l.effectiveBitDepth)
        assertEquals(BitDepthBasis.ALL_BITS, l.bitDepthBasis)
    }

    @Test
    fun resampledSixteenBitAudioCannotBeShownToBePadded() {
        // Linear interpolation to another rate puts every sample between the 16-bit levels.
        val src = FloatArray(60_000) { (floor(0.5 * 32767 * sin(2 * PI * 997.0 * it / 44100)) / 32768.0).toFloat() }
        val ratio = 44100.0 / 48000.0
        val out = FloatArray(55_000) { i ->
            val p = i * ratio
            val a = src[p.toInt()]
            val b = src[p.toInt() + 1]
            (a + (b - a) * (p - p.toInt())).toFloat()
        }
        val l = levels(out)
        assertEquals(24, l.effectiveBitDepth)
        assertFalse(BitDepth.isPadded(24, l.effectiveBitDepth))
    }

    @Test
    fun tooFewSamplesGiveNoAnswer() {
        val few = levels(sixteen(1_000, 7))
        assertNull(few.effectiveBitDepth)
        assertEquals(BitDepthBasis.UNKNOWN, few.bitDepthBasis)
        val silent = levels(FloatArray(50_000))
        assertNull(silent.effectiveBitDepth)
    }

    @Test
    fun paddingIsReadAgainstWhatTheFileCanHold() {
        assertEquals(24, BitDepth.capacity(32))
        assertEquals(16, BitDepth.capacity(16))
        assertNull(BitDepth.capacity(null))
        assertTrue(BitDepth.isPadded(24, 16))
        assertTrue(BitDepth.isPadded(32, 16))
        assertFalse(BitDepth.isPadded(16, 16))
        assertFalse(BitDepth.isPadded(32, 24))
        assertFalse(BitDepth.isPadded(null, 16))
        assertFalse(BitDepth.isPadded(24, null))
        assertEquals(emptyList<Note>(), BitDepth.paddingNote(24, 24))
        assertEquals(listOf(Note(NoteKey.BITS_PADDED, 16, 24)), BitDepth.paddingNote(24, 16))
        assertTrue(Notes.english(BitDepth.paddingNote(24, 16)).contains("16-bit audio stored as 24-bit"))
    }

    @Test
    fun labelsDescribeWhatWasMeasured() {
        assertEquals("16-bit data in 24-bit file", BitDepth.label(24, 16, BitDepthBasis.ZERO_PADDED))
        assertEquals("Uses all 24 bits", BitDepth.label(24, 24, BitDepthBasis.ALL_BITS))
        assertEquals("Uses all 24 bits", BitDepth.label(32, 24, BitDepthBasis.ALL_BITS))
        assertEquals("16-bit", BitDepth.label(16, 16, BitDepthBasis.ZERO_PADDED))
    }

    @Test
    fun theDecodedFormatIsReadFromTheOutputStreamNotTheInput() {
        val log = """
            Input #0, mov,mp4,m4a,3gp,3g2,mj2, from 'x.m4a':
              Stream #0:0[0x1](und): Audio: aac (HE-AACv2) (mp4a / 0x6134706D), 24000 Hz, mono, fltp, 48 kb/s
            Output #0, f32le, to '/cache/pcm_1.f32':
              Stream #0:0(und): Audio: pcm_f32le, 48000 Hz, stereo, flt, 3072 kb/s
        """.trimIndent()
        val f = DecodedFormat.parse(log)
        assertNotNull(f)
        assertEquals(48_000, f!!.sampleRate)
        assertEquals(2, f.channels)
        assertEquals("32-bit float, 48.0 kHz, stereo", f.summary)
    }

    @Test
    fun layoutNamesMapToChannelCounts() {
        fun ch(layout: String) = DecodedFormat.parse("Output #0, f32le, to 'a':\n  Stream #0:0: Audio: pcm_f32le, 96000 Hz, $layout, flt, 9 kb/s")?.channels
        assertEquals(1, ch("mono"))
        assertEquals(6, ch("5.1"))
        assertEquals(6, ch("5.1(side)"))
        assertEquals(8, ch("7.1"))
        assertEquals(3, ch("2.1"))
        assertEquals(4, ch("quad"))
        assertEquals(5, ch("5 channels"))
        assertNull(ch("something odd"))
        assertNull(DecodedFormat.parse("no streams here"))
        assertNull(DecodedFormat.parse(null))
    }
}
