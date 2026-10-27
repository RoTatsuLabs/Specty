package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.ui.spectrogram.ReportAspect
import com.rotatsulabs.specty.ui.spectrogram.ReportFit
import com.rotatsulabs.specty.ui.spectrogram.ReportOptions
import com.rotatsulabs.specty.ui.spectrogram.ReportSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportOptionsTest {
    @Test
    fun defaultsShowEverythingButLoudness() {
        val o = ReportOptions()
        assertFalse(o.has(ReportSection.Loudness))
        assertTrue(o.has(ReportSection.Spectrogram))
        assertEquals(ReportAspect.Portrait, o.aspect)
    }

    @Test
    fun maskRoundTrips() {
        val o = ReportOptions(ReportAspect.Landscape).toggled(ReportSection.Loudness, true).toggled(ReportSection.Footer, false)
        val back = ReportOptions.of(ReportAspect.Landscape.ordinal, o.sectionMask())
        assertEquals(o, back)
    }

    @Test
    fun negativeMaskMeansDefaults() {
        assertEquals(ReportOptions.DEFAULT_SECTIONS, ReportOptions.of(1, -1).sections)
        assertEquals(ReportAspect.Square, ReportOptions.of(1, -1).aspect)
    }

    @Test
    fun badAspectIndexIsClamped() {
        assertEquals(ReportAspect.Landscape, ReportOptions.of(99, -1).aspect)
        assertEquals(ReportAspect.Portrait, ReportOptions.of(-3, -1).aspect)
    }

    @Test
    fun sizesMatchTheirLabels() {
        assertEquals(1080 to 1920, ReportAspect.Portrait.width to ReportAspect.Portrait.height)
        assertEquals(1080 to 1080, ReportAspect.Square.width to ReportAspect.Square.height)
        assertEquals(1920 to 1080, ReportAspect.Landscape.width to ReportAspect.Landscape.height)
    }

    @Test
    fun fitScaleShrinksContentToTheRoom() {
        val s = ReportFit.scale(500f) { it * 1000f }
        assertEquals(0.5f, s, 0.01f)
    }

    @Test
    fun fitScaleIsClamped() {
        assertEquals(ReportFit.MAX, ReportFit.scale(1_000_000f) { it * 10f }, 0.0001f)
        assertEquals(ReportFit.MIN, ReportFit.scale(1f) { it * 10_000f }, 0.0001f)
    }

    @Test
    fun extraGapIsSharedAndCapped() {
        assertEquals(10f, ReportFit.extraGap(40f, 5, 30f), 0.001f)
        assertEquals(30f, ReportFit.extraGap(1000f, 5, 30f), 0.001f)
        assertEquals(0f, ReportFit.extraGap(-5f, 5, 30f), 0.001f)
    }
}
