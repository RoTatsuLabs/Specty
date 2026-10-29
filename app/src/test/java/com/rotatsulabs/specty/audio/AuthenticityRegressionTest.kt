package com.rotatsulabs.specty.audio

import com.rotatsulabs.specty.ui.components.Fmt
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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Verdicts are built from spectra shaped by hand, so each rule is tested on its own, plus a few full runs through the
 * analyzers for determinism and for independence from the spectrogram settings.
 */
class AuthenticityRegressionTest {
    private val rate = 44100
    private val bins = AnalysisVersion.AUTH_FFT / 2
    private val binHz = rate.toDouble() / AnalysisVersion.AUTH_FFT

    private fun info(codec: String = "flac", sampleRate: Int = rate, depth: Int? = 24, profile: String? = null) =
        AudioInfo("t", codec, null, sampleRate, 2, depth, "s32", null, 30.0, 1000, profile = profile)

    /** Music-like level in dB per bin: 6 dB per octave downwards from 1.5 kHz. */
    private fun base(f: Double) = -45.0 - 6.0 * ln(max(f, 200.0) / 1500.0) / ln(2.0)

    private fun spectrum(rateHz: Int = rate, level: (Double) -> Double): FloatArray {
        val hz = rateHz.toDouble() / AnalysisVersion.AUTH_FFT
        return FloatArray(bins) { level(it * hz).toFloat() }
    }

    private val fullBand = spectrum { base(it) }
    private fun cliffAt(cut: Double, floor: Double = -135.0) = spectrum { if (it < cut) base(it) else floor }

    private fun spectra(whole: FloatArray, sections: List<FloatArray?>, sampleRate: Int = rate) =
        AuthSpectra(sampleRate, AnalysisVersion.AUTH_FFT, whole, sections)

    private fun same(s: FloatArray, n: Int = 12) = List(n) { s }

    private fun assess(whole: FloatArray, sections: List<FloatArray?>, info: AudioInfo = info(), bits: Int? = null) =
        AuthenticityAnalyzer.assess(info, spectra(whole, sections, info.sampleRate), bits)

    @Test
    fun fullBandLosslessAboveFortyEightKilohertzIsHiRes() {
        assertEquals(Verdict.HI_RES, assess(fullBand, same(fullBand), info(sampleRate = 96000)).verdict)
        assertEquals(Verdict.GENUINE, assess(fullBand, same(fullBand), info(sampleRate = 48000)).verdict)
    }

    // ---- cutoffs and the Nyquist limit ------------------------------------------------------

    @Test
    fun fullBandAudioHasNoCutoffAndNyquistIsNeverOffered() {
        val r = assess(fullBand, same(fullBand))
        assertEquals(Verdict.GENUINE, r.verdict)
        assertEquals(SpectrumShape.FULL_BAND, r.shape)
        assertNull(r.cutoffHz)
        assertFalse(r.hasCliff)
        assertNull(r.emptyAboveHz)
        assertTrue(r.segmentCutoffs.all { it == 0f })
        val (label, value) = Fmt.spectralEdge(r)
        assertEquals("Spectral Cutoff", label)
        assertEquals("None found", value)
    }

    @Test
    fun anEdgeAtTheVeryTopOfTheBandIsNotACutoff() {
        val edge = spectrum { if (it < rate / 2.0 * 0.9858) base(it) else -150.0 }
        val d = AuthenticityAnalyzer.detect(edge, rate, AnalysisVersion.AUTH_FFT)
        assertNull(d.cutoff)
        assertEquals(SpectrumShape.FULL_BAND, d.shape)
    }

    @Test
    fun aSteepSixteenKilohertzEdgeIsReportedWithItsFrequency() {
        val r = assess(cliffAt(16_000.0), same(cliffAt(16_000.0)))
        assertEquals(Verdict.LOSSY_SOURCE, r.verdict)
        assertEquals(SpectrumShape.ABRUPT_CUTOFF, r.shape)
        assertTrue("cutoff ${r.cutoffHz}", kotlin.math.abs(r.cutoffHz!! - 16_000.0) < 600.0)
        assertEquals("Spectral Cutoff", Fmt.spectralEdge(r).first)
    }

    @Test
    fun aGradualRolloffIsAnEmptyBandNotACutoff() {
        val soft = spectrum { max(base(it) - max(0.0, it - 12_000.0) / 1000.0 * 11.0, -135.0) }
        val r = assess(soft, same(soft))
        assertEquals(SpectrumShape.GRADUAL_ROLLOFF, r.shape)
        assertEquals(Verdict.BAND_LIMITED, r.verdict)
        assertNull(r.cutoffHz)
        assertNotNull(r.emptyAboveHz)
        assertTrue("empty above ${r.emptyAboveHz}", r.emptyAboveHz!! in 14_000.0..20_500.0)
        assertTrue("confidence ${r.confidence}", r.confidence in 5..75)
        assertEquals("Spectral Roll-off", Fmt.spectralEdge(r).first)
    }

    // ---- silent, low-information and ambiguous audio -----------------------------------------

    @Test
    fun silenceIsInconclusiveWithNoConfidence() {
        val silent = spectrum { -140.0 }
        val r = assess(silent, same(silent))
        assertEquals(Verdict.INCONCLUSIVE, r.verdict)
        assertEquals(0, r.confidence)
        assertNull(r.cutoffHz)
        assertEquals("Not determined", Fmt.spectralEdge(r).second)
    }

    @Test
    fun aSingleToneHasNothingToJudge() {
        val tone = spectrum { if (kotlin.math.abs(it - 440.0) < binHz) -10.0 else -150.0 }
        val r = assess(tone, same(tone))
        assertEquals(Verdict.INCONCLUSIVE, r.verdict)
        assertEquals(0, r.confidence)
    }

    @Test
    fun aFaintTopEndThatCouldBeNoiseOrQuietContentIsInconclusive() {
        val faint = spectrum { f ->
            when {
                f < 18_000.0 -> base(f)
                f < 20_000.0 -> base(18_000.0) - (f - 18_000.0) / 2000.0 * (57.0 + base(18_000.0) - base(f))
                else -> -48.0 - 57.0
            }
        }
        val d = AuthenticityAnalyzer.detect(faint, rate, AnalysisVersion.AUTH_FFT)
        assertEquals("gap ${d.gap}", SpectrumShape.UNCLEAR, d.shape)
        val r = assess(faint, same(faint))
        assertEquals(Verdict.INCONCLUSIVE, r.verdict)
        assertEquals(0, r.confidence)
    }

    @Test
    fun aVeryLowSampleRateIsTooNarrowToJudge() {
        val low = spectrum(8000) { base(it) }
        val r = assess(low, same(low), info(sampleRate = 8000))
        assertEquals(Verdict.INCONCLUSIVE, r.verdict)
    }

    // ---- band-limited masters versus suspicious roll-offs ------------------------------------

    @Test
    fun aTwentyKilohertzMasterFilterIsOnlyPossiblyLossyAndLessCertainThanAnMp3Edge() {
        val master = assess(cliffAt(20_000.0), same(cliffAt(20_000.0)))
        val mp3 = assess(cliffAt(16_000.0), same(cliffAt(16_000.0)))
        assertEquals(Verdict.POSSIBLE_LOSSY_SOURCE, master.verdict)
        assertEquals(Verdict.LOSSY_SOURCE, mp3.verdict)
        assertTrue("${master.confidence} < ${mp3.confidence}", master.confidence < mp3.confidence)
        assertTrue(master.note, master.note.contains("masters"))
    }

    @Test
    fun anEdgeJustBelowNyquistIsOnlyBandLimited() {
        val r = assess(cliffAt(21_300.0), same(cliffAt(21_300.0)))
        assertEquals(Verdict.BAND_LIMITED, r.verdict)
    }

    // ---- section evidence and confidence -----------------------------------------------------

    @Test
    fun genuineNeedsSectionsThatAgreeAndEarnsItsConfidenceFromThem() {
        val r = assess(fullBand, same(fullBand))
        assertEquals(Verdict.GENUINE, r.verdict)
        assertEquals(12, r.sectionsJudged)
        assertEquals(12, r.sectionsAgreeing)
        assertTrue("confidence ${r.confidence}", r.confidence >= 80)
    }

    @Test
    fun withoutSectionsAFullBandClaimIsWithdrawn() {
        for (sections in listOf(emptyList(), List(12) { null }, listOf(fullBand, fullBand, null))) {
            val r = assess(fullBand, sections)
            assertEquals(Verdict.INCONCLUSIVE, r.verdict)
            assertEquals(0, r.confidence)
            assertNull(r.cutoffHz)
        }
    }

    @Test
    fun sectionsThatDisagreeWithTheAverageGiveNoVerdict() {
        val cliff = cliffAt(16_000.0)
        val mixed = List(5) { cliff } + List(7) { fullBand }
        val r = assess(cliff, mixed)
        assertEquals(Verdict.INCONCLUSIVE, r.verdict)
        assertEquals(0, r.confidence)
        assertNull(r.cutoffHz)
        assertTrue(r.note, r.note.contains("disagree"))
        assertEquals(12, r.sectionsJudged)
        assertEquals(5, r.sectionsAgreeing)
    }

    @Test
    fun missingOrUnreadableSectionsAreNotPositiveEvidence() {
        val cliff = cliffAt(16_000.0)
        val full = assess(cliff, same(cliff)).confidence
        val none = assess(cliff, emptyList()).confidence
        val three = assess(cliff, same(cliff, 3)).confidence
        val threePlusUnreadable = assess(cliff, same(cliff, 3) + List(9) { null }).confidence
        assertTrue("none $none must stay under 50", none < 50)
        assertTrue("full $full beats none $none", full > none)
        assertEquals(three, threePlusUnreadable)
    }

    @Test
    fun aLimitedCliffVerdictSaysHowLittleWasChecked() {
        val cliff = cliffAt(16_000.0)
        val r = assess(cliff, emptyList())
        assertEquals(Verdict.LOSSY_SOURCE, r.verdict)
        assertTrue(r.note, r.note.contains("could be read clearly"))
    }

    // ---- facts from the container -----------------------------------------------------------

    @Test
    fun lossyCodecAndAtmosAreJudgedFromMetadataEvenWhenTheAudioIsSilent() {
        val silent = spectrum { -140.0 }
        val mp3 = assess(silent, same(silent), info("mp3"))
        assertEquals(Verdict.LOSSY_CODEC, mp3.verdict)
        assertEquals(99, mp3.confidence)
        val atmos = assess(silent, same(silent), info("eac3", profile = "Dolby Digital Plus + Dolby Atmos"))
        assertEquals(Verdict.DOLBY_ATMOS, atmos.verdict)
        assertEquals(99, atmos.confidence)
    }

    // ---- bit depth and verdict stay consistent ----------------------------------------------

    @Test
    fun paddedBitDepthIsReportedBesideTheVerdictWithoutChangingIt() {
        val plain = assess(fullBand, same(fullBand), info(depth = 24), bits = 24)
        val padded = assess(fullBand, same(fullBand), info(depth = 24), bits = 16)
        assertEquals(plain.verdict, padded.verdict)
        assertEquals(plain.confidence, padded.confidence)
        assertTrue(padded.note, padded.note.contains("16-bit") && padded.note.contains("24-bit"))
        assertFalse(plain.note.contains("bits carry"))
        // 16-bit audio in a 16-bit file, and 24 effective bits in a 32-bit file, are not padding.
        assertFalse(assess(fullBand, same(fullBand), info(depth = 16), bits = 16).note.contains("bits carry"))
        assertFalse(assess(fullBand, same(fullBand), info(depth = 32), bits = 24).note.contains("bits carry"))
    }

    // ---- determinism ------------------------------------------------------------------------

    private fun multitone(seconds: Int, maxHz: Double, seed: Long): File {
        val n = rate * seconds
        val freqs = generateSequence(200.0) { it + 60.0 }.takeWhile { it <= maxHz }.toList()
        val rnd = Random(seed)
        val ph = freqs.map { rnd.nextDouble() * 2 * PI }
        val gain = 0.3 / sqrt(freqs.size.toDouble())
        val bb = ByteBuffer.allocate(n * 8).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            var s = 0.0
            for (j in freqs.indices) s += sin(2 * PI * freqs[j] * i / rate + ph[j])
            val v = (s * gain + 0.002 * rnd.nextGaussian()).toFloat()
            bb.putFloat(v); bb.putFloat(v)
        }
        return File.createTempFile("auth", ".f32").also { it.deleteOnExit(); it.writeBytes(bb.array()) }
    }

    private fun summary(r: AuthenticityReport) =
        listOf(r.verdict, r.confidence, r.cutoffHz, r.cliffDropDb, r.shape, r.note, r.emptyAboveHz, r.encoderGuess,
            r.sectionsJudged, r.sectionsAgreeing, r.segmentCutoffs.toList())

    @Test
    fun theVerdictDoesNotDependOnSpectrogramSizeOrWindow() {
        val file = multitone(4, 16_000.0, 3)
        var first: List<Any?>? = null
        var firstWhole: FloatArray? = null
        for (w in WindowFn.entries) for (fft in listOf(1024, 2048, 4096)) {
            PcmFile(file, 2, rate).use { pcm ->
                val out = SpectrogramAnalyzer.analyze(pcm, fft, windowFn = w)
                val r = AuthenticityAnalyzer.assess(info(), out.auth, 24)
                if (first == null) {
                    first = summary(r); firstWhole = out.auth.whole
                } else {
                    assertEquals("$w/$fft", first, summary(r))
                    assertTrue("$w/$fft spectra", firstWhole!!.contentEquals(out.auth.whole))
                }
            }
        }
        assertEquals(Verdict.LOSSY_SOURCE, (first!![0]))
    }

    @Test
    fun identicalAudioGivesIdenticalResultsInAnyOrder() {
        val a = multitone(3, 16_000.0, 5)
        val b = multitone(3, 21_800.0, 6)
        fun run(f: File) = PcmFile(f, 2, rate).use { pcm ->
            val out = SpectrogramAnalyzer.analyze(pcm, 2048)
            val levels = LevelAnalyzer.analyze(pcm)
            summary(AuthenticityAnalyzer.assess(info(), out.auth, levels.effectiveBitDepth)) + levels.effectiveBitDepth + levels.bitDepthBasis
        }
        val a1 = run(a); val b1 = run(b); val b2 = run(b); val a2 = run(a)
        assertEquals(a1, a2)
        assertEquals(b1, b2)
        assertFalse(a1 == b1)
    }

    @Test
    fun decodedRateOverridesTheDeclaredRateForTheFrequencyAxis() {
        // The same spectrum read at the declared 22.05 kHz and at the decoded 44.1 kHz puts the edge at different places.
        val cliff = cliffAt(16_000.0)
        val declared = info(sampleRate = 22_050)
        val r = AuthenticityAnalyzer.assess(declared, spectra(cliff, same(cliff), 44_100), null)
        assertTrue("cutoff ${r.cutoffHz}", kotlin.math.abs(r.cutoffHz!! - 16_000.0) < 600.0)
    }

    // ---- sample evidence and noise floor (checks from Mornye) -------------------------------

    private val lsbVar = Math.pow(Math.pow(2.0, -15.0), 2.0) / 12.0

    private fun assessWith(
        whole: FloatArray, info: AudioInfo, floor: Double?, finding: UpsampleEvidence.Finding? = null,
    ) = AuthenticityAnalyzer.assess(
        info, AuthSpectra(info.sampleRate, AnalysisVersion.AUTH_FFT, whole, same(whole), floor), null, finding,
    )

    @Test
    fun aRepeatedSamplePatternIsProofOfUpsamplingWhateverTheSpectrumShows() {
        val finding = UpsampleEvidence.Finding(UpsampleEvidence.Kind.SAMPLE_HOLD, 2, 44_100)
        val r = assessWith(spectrum(96_000) { base(it) }, info(sampleRate = 96_000), null, finding)
        assertEquals(Verdict.UPSAMPLED, r.verdict)
        assertEquals(97, r.confidence)
        assertEquals(NoteKey.UPSAMPLE_HOLD, r.notes.first().key)
        assertEquals(listOf("2", "44.1"), r.notes.first().args)
    }

    @Test
    fun aFloorAtSixteenBitsBacksACutoffBasedUpsampledVerdict() {
        val cut = spectrum(96_000) { if (it < 21_500.0) base(it) else -135.0 }
        val plain = assessWith(cut, info(sampleRate = 96_000), null)
        assertEquals(Verdict.UPSAMPLED, plain.verdict)
        val backed = assessWith(cut, info(sampleRate = 96_000), lsbVar)
        assertEquals(Verdict.UPSAMPLED, backed.verdict)
        assertTrue(backed.confidence >= plain.confidence && backed.confidence <= 95)
        assertEquals(NoteKey.FLOOR_AT_16BIT, backed.notes.last().key)
    }

    @Test
    fun aFloorBelowSixteenBitsWithdrawsTheUpsampledVerdict() {
        val cut = spectrum(96_000) { if (it < 21_500.0) base(it) else -135.0 }
        val r = assessWith(cut, info(sampleRate = 96_000), lsbVar / 100)
        assertEquals(Verdict.BAND_LIMITED, r.verdict)
        assertEquals(NoteKey.BAND_LIMITED, r.notes.first().key)
        assertEquals(NoteKey.FLOOR_BELOW_16BIT, r.notes.last().key)
    }

    @Test
    fun sampleEvidenceNeverTouchesALossyCodec() {
        val finding = UpsampleEvidence.Finding(UpsampleEvidence.Kind.SAMPLE_HOLD, 2, 44_100)
        val r = assessWith(fullBand, info(codec = "mp3"), null, finding)
        assertEquals(Verdict.LOSSY_CODEC, r.verdict)
    }
}
