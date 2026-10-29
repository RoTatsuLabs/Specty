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
import kotlin.math.sin

/** The sample-and-hold and linear-interpolation checks, on decoded float PCM as the analyzer reads it. */
class UpsampleEvidenceTest {
    private fun pcm(samples: FloatArray, rate: Int, channels: Int = 1): PcmFile {
        val bb = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { bb.putFloat(it) }
        val file = File.createTempFile("upsample", ".f32").also { it.deleteOnExit(); it.writeBytes(bb.array()) }
        return PcmFile(file, channels, rate)
    }

    /** A 16-bit source: random music-like integers at the source rate, as floats the decoder would give. */
    private fun source(n: Int, seed: Long): IntArray {
        val rnd = Random(seed)
        var x = 0.0
        return IntArray(n) { i ->
            x = 0.9 * x + rnd.nextGaussian() * 1500.0
            (x + 8000.0 * sin(2 * PI * 440.0 * i / 44_100.0)).toInt().coerceIn(-32768, 32767)
        }
    }

    private fun hold(src: IntArray, ratio: Int) = FloatArray(src.size * ratio) { src[it / ratio] / 32768f }

    private fun line(src: IntArray, ratio: Int) = FloatArray((src.size - 1) * ratio) {
        val i = it / ratio
        val f = (it % ratio).toDouble() / ratio
        ((src[i] * (1 - f) + src[i + 1] * f) / 32768.0).toFloat()
    }

    @Test
    fun repeatedSamplesAreSampleAndHold() {
        pcm(hold(source(60_000, 1), 2), 88_200).use { p ->
            val f = UpsampleEvidence.scan(p)!!
            assertEquals(UpsampleEvidence.Kind.SAMPLE_HOLD, f.kind)
            assertEquals(2, f.ratio)
            assertEquals(44_100, f.sourceHz)
        }
        pcm(hold(source(60_000, 2), 4), 192_000).use { p ->
            val f = UpsampleEvidence.scan(p)!!
            assertEquals(UpsampleEvidence.Kind.SAMPLE_HOLD, f.kind)
            assertEquals(4, f.ratio)
            assertEquals(48_000, f.sourceHz)
        }
    }

    @Test
    fun straightLinesBetweenSamplesAreInterpolation() {
        pcm(line(source(60_000, 3), 2), 96_000).use { p ->
            val f = UpsampleEvidence.scan(p)!!
            assertEquals(UpsampleEvidence.Kind.LINEAR_INTERPOLATION, f.kind)
            assertEquals(2, f.ratio)
            assertEquals(48_000, f.sourceHz)
        }
    }

    @Test
    fun aNativeRecordingShowsNothing() {
        val rnd = Random(4)
        var x = 0.0
        val native = FloatArray(200_000) { x = 0.9 * x + rnd.nextGaussian() * 0.05; x.toFloat().coerceIn(-1f, 1f) }
        pcm(native, 96_000).use { assertNull(UpsampleEvidence.scan(it)) }
    }

    @Test
    fun aRateThatIsNotAWholeMultipleIsNotChecked() {
        pcm(hold(source(60_000, 5), 2), 88_000).use { assertNull(UpsampleEvidence.scan(it)) }
        pcm(source(30_000, 6).let { FloatArray(it.size) { i -> it[i] / 32768f } }, 44_100).use { assertNull(UpsampleEvidence.scan(it)) }
    }

    @Test
    fun aFileThatIsOnlyPartlyUpsampledGetsNoFinding() {
        val up = hold(source(30_000, 7), 2)
        val rnd = Random(8)
        var x = 0.0
        val native = FloatArray(up.size) { x = 0.9 * x + rnd.nextGaussian() * 0.05; x.toFloat().coerceIn(-1f, 1f) }
        pcm(up + native, 88_200).use { assertNull(UpsampleEvidence.scan(it)) }
    }

    @Test
    fun silenceIsNotJudged() {
        pcm(FloatArray(100_000), 96_000).use { assertNull(UpsampleEvidence.scan(it)) }
    }

    @Test
    fun onlyTheFirstChannelOfAStereoFileIsRead() {
        val l = hold(source(40_000, 9), 2)
        val inter = FloatArray(l.size * 2) { if (it % 2 == 0) l[it / 2] else 0.1f * sin(it * 0.37f) }
        pcm(inter, 88_200, 2).use { assertNotNull(UpsampleEvidence.scan(it)) }
    }
}
