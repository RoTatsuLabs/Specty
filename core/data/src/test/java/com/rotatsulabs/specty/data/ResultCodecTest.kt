package com.rotatsulabs.specty.data

import com.rotatsulabs.specty.audio.AnalysisVersion
import com.rotatsulabs.specty.audio.AudioInfo
import com.rotatsulabs.specty.audio.AuthenticityAnalyzer
import com.rotatsulabs.specty.audio.AuthenticityReport
import com.rotatsulabs.specty.audio.BitDepthBasis
import com.rotatsulabs.specty.audio.ChannelStats
import com.rotatsulabs.specty.audio.DecodedFormat
import com.rotatsulabs.specty.audio.LevelAnalyzer
import com.rotatsulabs.specty.audio.LevelStats
import com.rotatsulabs.specty.audio.PcmFile
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.audio.SpectrogramAnalyzer
import com.rotatsulabs.specty.audio.Note
import com.rotatsulabs.specty.audio.NoteKey
import com.rotatsulabs.specty.audio.SpectrumShape
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.audio.WindowFn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class ResultCodecTest {
    private fun info() = AudioInfo(
        "song.flac", "flac", "flac", 44100, 2, 24, "s32", 900_000L, 30.0, 4_000_000,
        tags = listOf("title" to "Song"), profile = null,
    )

    private fun stored(
        report: AuthenticityReport,
        decoded: DecodedFormat? = DecodedFormat(44100, 2),
    ): StoredResult {
        val levels = LevelStats(
            -1.0, -18.0, -14.0, -0.5, 3, 1_000_000, 16, listOf(ChannelStats(-1.0, -18.0)),
            drTt = 9.0, dcOffsets = listOf(0.0001), correlation = 0.5, balanceDb = null, timeline = null,
            bitDepthBasis = BitDepthBasis.ZERO_PADDED,
        )
        val sg = Spectrogram(2, 4, 44100, 8, 30.0, listOf(ByteArray(8) { it.toByte() }, ByteArray(8), ByteArray(8)), listOf("Channels", "Ch 1", "Ch 2"))
        return StoredResult(info(), levels, report, sg, decoded)
    }

    private fun assertSame(a: AuthenticityReport, b: AuthenticityReport) {
        assertEquals(a.verdict, b.verdict); assertEquals(a.cutoffHz, b.cutoffHz)
        assertEquals(a.cliffDropDb, b.cliffDropDb, 0.0); assertEquals(a.notes, b.notes); assertEquals(a.note, b.note)
        assertEquals(a.confidence, b.confidence); assertTrue(a.segmentCutoffs.contentEquals(b.segmentCutoffs))
        assertEquals(a.encoderGuess, b.encoderGuess); assertEquals(a.emptyAboveHz, b.emptyAboveHz)
        assertEquals(a.shape, b.shape); assertEquals(a.sectionsJudged, b.sectionsJudged)
        assertEquals(a.sectionsAgreeing, b.sectionsAgreeing)
    }

    @Test
    fun aResultWithoutACutoffComesBackWithoutOne() {
        val r = AuthenticityReport(Verdict.GENUINE, null, 0.0, listOf(Note(NoteKey.FULL_BAND)), 88, FloatArray(3), null, null, SpectrumShape.FULL_BAND, 12, 12)
        val back = ResultCodec.fromBytes(ResultCodec.toBytes(stored(r)))!!
        assertNull(back.authenticity.cutoffHz)
        assertFalse(back.authenticity.hasCliff)
        assertSame(r, back.authenticity)
    }

    @Test
    fun everyFieldSurvivesTheRoundTrip() {
        val r = AuthenticityReport(
            Verdict.LOSSY_SOURCE, 16_050.0, 74.5, listOf(Note(NoteKey.HARD_CUTOFF, "16.1"), Note(NoteKey.BITS_PADDED, 16, 24)), 91, floatArrayOf(16_000f, 0f, 16_100f), "MP3 around 128 kbps",
            16_300.0, SpectrumShape.ABRUPT_CUTOFF, 10, 9,
        )
        val s = stored(r)
        val back = ResultCodec.fromBytes(ResultCodec.toBytes(s))!!
        assertSame(r, back.authenticity)
        assertEquals(DecodedFormat(44100, 2), back.decoded)
        assertEquals(16, back.levels.effectiveBitDepth)
        assertEquals(BitDepthBasis.ZERO_PADDED, back.levels.bitDepthBasis)
        assertEquals(s.info, back.info)
        assertEquals(s.spectrogram.views.size, back.spectrogram.views.size)
        assertTrue(s.spectrogram.views[0].contentEquals(back.spectrogram.views[0]))
        assertNull(ResultCodec.fromBytes(ResultCodec.toBytes(stored(r, decoded = null)))!!.decoded)
    }

    @Test
    fun aCachedResultMatchesTheFreshOneItWasMadeFrom() {
        val rate = 44100
        val n = rate * 4
        val freqs = generateSequence(200.0) { it + 60.0 }.takeWhile { it <= 16_000.0 }.toList()
        val rnd = Random(9)
        val ph = freqs.map { rnd.nextDouble() * 2 * PI }
        val gain = 0.3 / sqrt(freqs.size.toDouble())
        val bb = ByteBuffer.allocate(n * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            var s = 0.0
            for (j in freqs.indices) s += sin(2 * PI * freqs[j] * i / rate + ph[j])
            val v = (s * gain).toFloat(); bb.putFloat(v); bb.putFloat(v)
        }
        val file = File.createTempFile("cache", ".f32").also { it.deleteOnExit(); it.writeBytes(bb.array()) }
        PcmFile(file, 2, rate).use { pcm ->
            val out = SpectrogramAnalyzer.analyze(pcm, 2048)
            val levels = LevelAnalyzer.analyze(pcm)
            val fresh = StoredResult(info(), levels, AuthenticityAnalyzer.assess(info(), out.auth, levels.effectiveBitDepth), out.spectrogram, DecodedFormat(rate, 2))
            val cached = ResultCodec.fromBytes(ResultCodec.toBytes(fresh))!!
            assertSame(fresh.authenticity, cached.authenticity)
            assertEquals(Verdict.LOSSY_SOURCE, cached.authenticity.verdict)
            assertEquals(fresh.levels.effectiveBitDepth, cached.levels.effectiveBitDepth)
            assertEquals(fresh.levels.bitDepthBasis, cached.levels.bitDepthBasis)
            assertEquals(fresh.levels.peakDb, cached.levels.peakDb, 0.0)
            assertTrue(fresh.spectrogram.views[0].contentEquals(cached.spectrogram.views[0]))
            // Analysing the same file again gives the same result as the one that was stored.
            val again = AuthenticityAnalyzer.assess(info(), SpectrogramAnalyzer.analyze(pcm, 2048).auth, levels.effectiveBitDepth)
            assertSame(cached.authenticity, again)
        }
    }

    // ---- invalidation ------------------------------------------------------------------------

    private fun sampleBytes() = ResultCodec.toBytes(
        stored(AuthenticityReport(Verdict.GENUINE, null, 0.0, listOf(Note(NoteKey.FULL_BAND)), 88, FloatArray(0), null, null, SpectrumShape.FULL_BAND, 12, 12)),
    )

    @Test
    fun aResultFromAnotherAlgorithmVersionIsNotReadBack() {
        val bytes = sampleBytes()
        assertNotNull(ResultCodec.fromBytes(bytes))
        val old = bytes.copyOf()
        ByteBuffer.wrap(old).putInt(4, AnalysisVersion.ALGORITHM - 1)
        assertNull(ResultCodec.fromBytes(old))
        ByteBuffer.wrap(old).putInt(4, AnalysisVersion.ALGORITHM + 1)
        assertNull(ResultCodec.fromBytes(old))
    }

    @Test
    fun aResultFromAnotherSchemaIsNotReadBack() {
        val old = sampleBytes().copyOf()
        ByteBuffer.wrap(old).putInt(0, 0x53504336) // the previous format, "SPC6"
        assertNull(ResultCodec.fromBytes(old))
    }

    @Test
    fun truncatedOrGarbledResultsAreRejected() {
        val bytes = sampleBytes()
        assertNull(ResultCodec.fromBytes(bytes.copyOf(bytes.size / 2)))
        assertNull(ResultCodec.fromBytes(ByteArray(0)))
        assertNull(ResultCodec.fromBytes(ByteArray(64) { 7 }))
    }

    @Test
    fun theIndexHeaderCarriesBothVersionsAndRejectsOlderOnes() {
        val header = CacheVersion.indexHeader
        assertTrue(CacheVersion.isCurrentIndex(header))
        assertTrue(header, header.endsWith(".${AnalysisVersion.ALGORITHM}"))
        assertTrue(header, header.contains(Integer.toHexString(CacheVersion.MAGIC)))
        assertFalse(CacheVersion.isCurrentIndex("v53504336"))
        assertFalse(CacheVersion.isCurrentIndex("v" + Integer.toHexString(CacheVersion.MAGIC)))
        assertFalse(CacheVersion.isCurrentIndex("v" + Integer.toHexString(CacheVersion.MAGIC) + ".${AnalysisVersion.ALGORITHM - 1}"))
        assertFalse(CacheVersion.isCurrentIndex(null))
    }

    @Test
    fun keysChangeWithTheFileTheSettingsAndTheAnalysisVersion() {
        val k = CacheKey.of("content://a/1", 100, 5, 2048)
        assertEquals(k, CacheKey.of("content://a/1", 100, 5, 2048))
        assertEquals(k, CacheKey.of("content://a/1", 100, 5, 2048, WindowFn.Hann))
        assertNotEquals(k, CacheKey.of("content://a/2", 100, 5, 2048))
        assertNotEquals(k, CacheKey.of("content://a/1", 101, 5, 2048))
        assertNotEquals(k, CacheKey.of("content://a/1", 100, 6, 2048))
        assertNotEquals(k, CacheKey.of("content://a/1", 100, 5, 4096))
        assertNotEquals(k, CacheKey.of("content://a/1", 100, 5, 2048, WindowFn.Hamming))
        // A key made by the previous algorithm version, hashed the same way, never equals a current key.
        val previous = "content://a/1|100|5|2048|s${AnalysisVersion.SCHEMA}.a${AnalysisVersion.ALGORITHM - 1}"
        val old = MessageDigest.getInstance("SHA-1").digest(previous.toByteArray()).joinToString("") { "%02x".format(it) }
        assertNotEquals(old, k)
        val legacy = MessageDigest.getInstance("SHA-1").digest("content://a/1|100|5|2048".toByteArray()).joinToString("") { "%02x".format(it) }
        assertNotEquals(legacy, k)
    }
}
