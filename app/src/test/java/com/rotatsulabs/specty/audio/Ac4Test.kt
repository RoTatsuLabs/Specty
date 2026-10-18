package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ac4Test {
    private fun info(codec: String) = AudioInfo("a.m4a", codec, "mov,mp4", 48000, 2, null, null, 256_000L, 233.6, 1)

    @Test
    fun ac4IsALossyCodecThatNeedsTheDeviceDecoder() {
        assertTrue(Codecs.isLossy("ac4"))
        assertTrue(Codecs.needsPlatformDecoder("AC4"))
        assertEquals("Dolby AC-4", Codecs.displayName("ac4"))
    }

    @Test
    fun otherCodecsStayOnFfmpeg() {
        for (codec in listOf("aac", "eac3", "flac", "alac", "mp3")) assertFalse(codec, Codecs.needsPlatformDecoder(codec))
    }

    @Test
    fun anUnmeasuredFileIsReportedFromItsContainerOnly() {
        val report = AuthenticityAnalyzer.unmeasured(info("ac4"))
        assertEquals(Verdict.LOSSY_CODEC, report.verdict)
        assertEquals(99, report.confidence)
        assertEquals(null, report.cutoffHz)
        assertTrue(report.note, report.note.contains("Dolby AC-4"))
        assertTrue(report.note, report.note.contains("could not be measured"))
    }
}
