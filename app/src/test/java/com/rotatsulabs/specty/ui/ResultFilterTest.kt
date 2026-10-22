package com.rotatsulabs.specty.ui

import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.screens.ResultFilter
import com.rotatsulabs.specty.ui.screens.accepts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResultFilterTest {
    @Test
    fun everyVerdictAndNoResultBelongToExactlyOneKind() {
        for (v in Verdict.entries) assertTrue(v.name, ResultFilter.of(v) != ResultFilter.Unchecked)
        assertEquals(ResultFilter.Unchecked, ResultFilter.of(null))
        assertEquals(ResultFilter.Lossless, ResultFilter.of(Verdict.GENUINE))
        assertEquals(ResultFilter.HiRes, ResultFilter.of(Verdict.HI_RES))
    }

    @Test
    fun needsALookMatchesWhatTheScanFlags() {
        val flagged = setOf(Verdict.LOSSY_SOURCE, Verdict.UPSAMPLED, Verdict.POSSIBLE_LOSSY_SOURCE)
        for (v in Verdict.entries) assertEquals(v.name, v in flagged, ResultFilter.of(v) == ResultFilter.NeedsLook)
    }

    @Test
    fun nothingChosenShowsEverythingAndAChoiceShowsOnlyItsKinds() {
        assertTrue(emptySet<ResultFilter>().accepts(null))
        assertTrue(emptySet<ResultFilter>().accepts(Verdict.LOSSY_SOURCE))
        val some = setOf(ResultFilter.Unchecked, ResultFilter.NeedsLook)
        assertTrue(some.accepts(null))
        assertTrue(some.accepts(Verdict.UPSAMPLED))
        assertFalse(some.accepts(Verdict.GENUINE))
        assertFalse(some.accepts(Verdict.LOSSY_CODEC))
    }

    @Test
    fun countsAddUpToTheLibrary() {
        val verdicts = listOf(null, null, Verdict.GENUINE, Verdict.HI_RES, Verdict.UPSAMPLED, Verdict.LOSSY_SOURCE, Verdict.INCONCLUSIVE)
        val counts = ResultFilter.counts(verdicts)
        assertEquals(verdicts.size, counts.values.sum())
        assertEquals(2, counts.getValue(ResultFilter.Unchecked))
        assertEquals(2, counts.getValue(ResultFilter.NeedsLook))
        assertEquals(0, counts.getValue(ResultFilter.Atmos))
        assertEquals(ResultFilter.entries.toSet(), counts.keys)
    }

    @Test
    fun theMaskRoundTripsEverySelection() {
        val all = ResultFilter.entries
        for (mask in 0 until (1 shl all.size)) assertEquals(mask, ResultFilter.toMask(ResultFilter.fromMask(mask)))
        assertEquals(setOf(ResultFilter.Lossless, ResultFilter.Atmos), ResultFilter.fromMask(ResultFilter.toMask(setOf(ResultFilter.Lossless, ResultFilter.Atmos))))
    }

    @Test
    fun everyKindHasATranslatedLabelAndHint() {
        var root: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (root != null && !File(root, "gradle/libs.versions.toml").exists()) root = root.parentFile
        val names = listOf("unchecked", "lossless", "needs_look", "lossy_format", "atmos", "unclear").flatMap { listOf("filter_$it", "filter_${it}_hint") } +
            "filter_hires_hint" + listOf("filter_open", "filter_title", "filter_subtitle", "filter_reset", "filter_clear", "filter_remove", "filter_empty_title", "filter_empty_message")
        for (dir in listOf("values", "values-in", "values-zh-rCN")) {
            val xml = File(root, "app/src/main/res/$dir/strings.xml").readText()
            for (n in names) assertTrue("$dir is missing $n", """<string name="$n"""" in xml)
            assertTrue("$dir is missing filter_show", """<plurals name="filter_show"""" in xml)
        }
    }
}
