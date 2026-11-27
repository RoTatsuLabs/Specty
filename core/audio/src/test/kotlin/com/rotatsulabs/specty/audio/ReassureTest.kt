package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.math.ln
import kotlin.math.max

class ReassureTest {
    private fun info(rate: Int = 48_000) = AudioInfo("t", "alac", null, rate, 2, 24, "s32", null, 30.0, 1000)

    private fun report(
        verdict: Verdict,
        vararg notes: Note,
        cutoff: Double? = null,
        emptyAbove: Double? = null,
        hiRes: HiRes? = null,
    ) = AuthenticityReport(verdict, cutoff, 40.0, notes.toList(), confidence = 60, emptyAboveHz = emptyAbove, hiRes = hiRes)

    @Test
    fun aSharpEdgeNothingElseBacksIsLosslessWithAPlainNote() {
        val r = AuthenticityAnalyzer.reassure(report(Verdict.BAND_LIMITED, Note(NoteKey.BAND_LIMITED, "23.2"), cutoff = 23_200.0), info())
        assertEquals(Verdict.GENUINE, r.verdict)
        assertEquals(listOf(Note(NoteKey.CONTENT_REACHES, "23.2")), r.notes)
        assertEquals(23_200.0, r.cutoffHz!!, 0.0)
        assertNull(r.evidence)
    }

    @Test
    fun aRolloffIsLosslessAndLosesTheLowBandNote() {
        val r = AuthenticityAnalyzer.reassure(
            report(Verdict.ROLLOFF, Note(NoteKey.ROLLOFF, "16.0"), Note(NoteKey.ROLLOFF_LOW), emptyAbove = 16_000.0), info(),
        )
        assertEquals(Verdict.GENUINE, r.verdict)
        assertEquals(listOf(Note(NoteKey.CONTENT_REACHES, "16.0")), r.notes)
    }

    @Test
    fun aSpectrumThatCouldNotBeReadIsLosslessWithoutNotes() {
        for (key in listOf(NoteKey.FAINT_TOP, NoteKey.TOO_LITTLE_CLEAR)) {
            val r = AuthenticityAnalyzer.reassure(report(Verdict.INCONCLUSIVE, Note(key)), info())
            assertEquals(Verdict.GENUINE, r.verdict)
            assertEquals(emptyList<Note>(), r.notes)
        }
        val disagree = AuthenticityAnalyzer.reassure(report(Verdict.INCONCLUSIVE, Note(NoteKey.SECTIONS_DISAGREE, 3, 9)), info())
        assertEquals(Verdict.GENUINE, disagree.verdict)
    }

    @Test
    fun noteAboutBitDepthAndFloorSurvive() {
        val r = AuthenticityAnalyzer.reassure(
            report(Verdict.ROLLOFF, Note(NoteKey.ROLLOFF, "19.0"), Note(NoteKey.BITS_PADDED, 16, 24), emptyAbove = 19_000.0), info(),
        )
        assertEquals(listOf(NoteKey.CONTENT_REACHES, NoteKey.BITS_PADDED), r.notes.map { it.key })
    }

    @Test
    fun noSignalAndNoDecoderStayInconclusive() {
        val silent = report(Verdict.INCONCLUSIVE, Note(NoteKey.NO_SIGNAL))
        assertSame(silent, AuthenticityAnalyzer.reassure(silent, info()))
    }

    @Test
    fun aVerifiedHiResKeepsItsLabelAndAnUnconfirmedOneDoesNot() {
        val verified = AuthenticityAnalyzer.reassure(report(Verdict.ROLLOFF, emptyAbove = 30_000.0, hiRes = HiRes.VERIFIED), info(96_000))
        assertEquals(Verdict.HI_RES, verified.verdict)
        val unconfirmed = AuthenticityAnalyzer.reassure(report(Verdict.ROLLOFF, emptyAbove = 30_000.0, hiRes = HiRes.UNCONFIRMED), info(96_000))
        assertEquals(Verdict.GENUINE, unconfirmed.verdict)
        assertEquals(HiRes.UNCONFIRMED, unconfirmed.hiRes)
    }

    @Test
    fun realProblemsAreLeftAlone() {
        for (v in listOf(Verdict.LOSSY_SOURCE, Verdict.POSSIBLE_LOSSY_SOURCE, Verdict.UPSAMPLED, Verdict.LOSSY_CODEC, Verdict.GENUINE, Verdict.HI_RES)) {
            val r = report(v, cutoff = 16_000.0)
            assertSame(v.name, r, AuthenticityAnalyzer.reassure(r, info()))
        }
    }

    // The case from the library: a 48 kHz, 24-bit ALAC file whose spectrum ends at a sharp edge near 23.2 kHz.
    @Test
    fun aMasterFilteredNearNyquistReadsAsLossless() {
        val rate = 48_000
        val fft = AnalysisVersion.AUTH_FFT
        val binHz = rate.toDouble() / fft
        val whole = FloatArray(fft / 2) {
            val f = it * binHz
            if (f < 23_200.0) (-45.0 - 6.0 * ln(max(f, 200.0) / 1500.0) / ln(2.0)).toFloat() else -135f
        }
        val assessed = AuthenticityAnalyzer.assess(info(rate), AuthSpectra(rate, fft, whole, List(12) { whole }), 24)
        assertEquals(Verdict.BAND_LIMITED, assessed.verdict)
        assertEquals(Verdict.GENUINE, AuthenticityAnalyzer.reassure(assessed, info(rate)).verdict)
    }
}
