package com.rotatsulabs.specty.audio

import com.rotatsulabs.specty.batch.ScanNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FailuresTest {
    private val failRes = mapOf(
        FailKey.STORAGE_FULL to ("fail_storage_full" to 0), FailKey.FILE_TOO_LONG to ("fail_too_long" to 1),
        FailKey.NOT_ENOUGH_STORAGE to ("fail_not_enough_storage" to 1), FailKey.MORE_AUDIO_THAN_EXPECTED to ("fail_more_audio" to 0),
        FailKey.NO_AUDIO to ("fail_no_audio" to 0), FailKey.CANNOT_OPEN to ("fail_cannot_open" to 0),
        FailKey.NOT_AUDIO to ("fail_not_audio" to 0), FailKey.NO_AUDIO_STREAM to ("fail_no_stream" to 0),
        FailKey.NO_USABLE_FORMAT to ("fail_no_format" to 0), FailKey.DECODE_FAILED to ("fail_decode" to 1),
    )
    private val scanRes = mapOf(
        ScanNote.TooManyFailures to ("scan_note_failures" to 0), ScanNote.LowMemory to ("scan_note_memory" to 0),
        ScanNote.TimeLimit to ("scan_note_time_limit" to 0), ScanNote.Cooling to ("scan_note_cooling" to 0),
        ScanNote.Interrupted to ("scan_note_interrupted" to 0), ScanNote.StartFailed to ("scan_note_start_failed" to 1),
    )
    private val stageRes = mapOf(
        AnalysisStage.READING to "stage_reading", AnalysisStage.DECODING to "stage_decoding", AnalysisStage.ANALYSING to "stage_analysing",
    )

    private fun res(): File {
        var root: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (root != null && !File(root, "gradle/libs.versions.toml").exists()) root = root.parentFile
        return File(root, "app/src/main/res")
    }

    private fun checkTranslated(name: String, args: Int) {
        for (dir in listOf("values", "values-in", "values-zh-rCN")) {
            val xml = File(res(), "$dir/strings.xml").readText()
            val body = Regex("""<string name="$name"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)
            assertTrue("$dir is missing $name", body != null)
            val used = Regex("""%(\d)\$""").findAll(body!!).map { it.groupValues[1].toInt() }.toSet()
            assertTrue("$dir $name uses $used, only $args exist", (1..args).toSet().containsAll(used))
            if (dir == "values") assertEquals("$name in English must use every argument", (1..args).toSet(), used)
        }
    }

    @Test
    fun everyFailureHasEnglishAndTranslatedText() {
        assertEquals(FailKey.entries.toSet(), failRes.keys)
        for ((key, r) in failRes) {
            val failure = Failure(key, List(r.second) { "12" })
            assertTrue(key.name, Failures.english(failure).isNotBlank())
            checkTranslated(r.first, r.second)
        }
    }

    @Test
    fun theStorageRefusalKeepsItsWording() {
        assertEquals("Storage is almost full. Free some space and try again.", StorageGuard.refusal(1, 0))
        assertTrue(StorageGuard.refusal(3_000_000_000L, 50L * 1024 * 1024 * 1024).contains("3000 MB"))
        assertEquals(FailKey.NOT_ENOUGH_STORAGE, StorageGuard.failure(1_500_000_000L, 1_000_000_000L).key)
    }

    @Test
    fun everyScanNoteAndStageIsTranslated() {
        assertEquals(ScanNote.entries.toSet() - ScanNote.None, scanRes.keys)
        for ((_, r) in scanRes) checkTranslated(r.first, r.second)
        assertEquals(AnalysisStage.entries.toSet(), stageRes.keys)
        for ((_, name) in stageRes) checkTranslated(name, 0)
        for (name in listOf("fail_generic", "storage_internal", "library_error")) checkTranslated(name, 0)
    }
}
