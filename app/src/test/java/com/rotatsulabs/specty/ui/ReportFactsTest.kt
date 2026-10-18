package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.audio.AudioInfo
import com.rotatsulabs.specty.audio.ChannelStats
import com.rotatsulabs.specty.audio.LevelStats
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.spectrogram.ReportFacts
import com.rotatsulabs.specty.ui.spectrogram.ReportFacts.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportFactsTest {
    private fun info(codec: String = "flac", rate: Int = 44100, bits: Int? = 16, channels: Int = 2, profile: String? = null) = AudioInfo(
        displayName = "song.flac", codec = codec, container = "flac", sampleRate = rate, channels = channels, bitDepth = bits,
        sampleFormat = null, bitrateBps = null, durationSec = 255.0, sizeBytes = 30L * 1024 * 1024, profile = profile,
    )

    private fun levels(lufs: Double? = -13.3, depth: Int? = 24, dr: Double? = null, corr: Double? = null) = LevelStats(
        peakDb = -1.2, rmsDb = -18.9, lufs = lufs, truePeakDbtp = -0.9, clippedSamples = 1_500, framesPerChannel = 1000,
        effectiveBitDepth = depth, channels = listOf(ChannelStats(-1.2, -18.9)), drTt = dr, correlation = corr,
    )

    @Test
    fun theTagFollowsTheVerdict() {
        assertEquals(Quality.Lossless, ReportFacts.quality(info(), Verdict.GENUINE))
        assertEquals(Quality.HiRes, ReportFacts.quality(info(rate = 96000), Verdict.HI_RES))
        assertNull(ReportFacts.quality(info(), Verdict.LOSSY_SOURCE))
        assertNull(ReportFacts.quality(info("mp3"), Verdict.LOSSY_CODEC))
    }

    @Test
    fun atmosWinsOverTheLosslessTags() {
        val atmos = info("eac3", 48000, null, 6, "Dolby Digital Plus + Dolby Atmos")
        assertEquals(Quality.Atmos, ReportFacts.quality(atmos, Verdict.DOLBY_ATMOS))
        assertEquals(Quality.Atmos, ReportFacts.quality(atmos, Verdict.GENUINE))
    }

    @Test
    fun everyVerdictHasAHeadline() {
        for (v in Verdict.entries) assertTrue(v.name, ReportFacts.headline(v).isNotBlank())
    }

    @Test
    fun theFileFactsReadInOrderAndSkipWhatIsUnknown() {
        assertEquals(listOf("FLAC", "44.1 kHz", "16-bit", "Stereo", "4:15", "30.0 MB"), ReportFacts.chips(info()))
        val chips = ReportFacts.chips(info(bits = null, channels = 6))
        assertFalse(chips.any { it.endsWith("-bit") })
        assertTrue("6 channels" in chips)
        assertTrue("Mono" in ReportFacts.chips(info(channels = 1)))
    }

    @Test
    fun theHeadlineMeasurementsComeFirst() {
        val m = ReportFacts.metrics(levels())
        assertEquals(listOf("Dynamic Range", "Loudness", "True Peak"), m.take(3).map { it.label })
        assertEquals("dB", m[0].unit)
        assertEquals("-13.3", m[1].value)
        assertEquals("LUFS", m[1].unit)
        assertEquals("dBTP", m[2].unit)
    }

    @Test
    fun valuesThatWereNotMeasuredAreDashedOrLeftOut() {
        val m = ReportFacts.metrics(levels(lufs = null, depth = null))
        assertEquals("—", m.first { it.label == "Loudness" }.value)
        assertEquals("", m.first { it.label == "Loudness" }.unit)
        assertEquals("—", m.first { it.label == "Effective Bit Depth" }.value)
        assertFalse(m.any { it.label == "DR Meter" || it.label == "Stereo Correlation" })
        val full = ReportFacts.metrics(levels(dr = 11.4, corr = 0.873))
        assertEquals("DR11", full.first { it.label == "DR Meter" }.value)
        assertEquals("0.87", full.first { it.label == "Stereo Correlation" }.value)
        assertEquals("1.5K", full.first { it.label == "Clipped Samples" }.value)
    }

    @Test
    fun fileNamesKeepAnyScriptAndDropWhatIsNotAllowed() {
        assertEquals("横山 克 Baby Baby-report-20261018.png", ReportFacts.fileName("横山 克 Baby Baby", "20261018"))
        assertEquals("AC DC Back In Black-report-x.png", ReportFacts.fileName("AC/DC: Back In Black?", "x"))
        assertEquals("analysis-report-x.png", ReportFacts.fileName("  ///  ", "x"))
        assertEquals("Wait-report-x.png", ReportFacts.fileName("Wait...", "x"))
    }

    @Test
    fun aLongTitleIsCutShort() {
        val name = ReportFacts.fileName("a".repeat(200), "x")
        assertEquals(60 + "-report-x.png".length, name.length)
    }
}
