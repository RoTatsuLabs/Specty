package com.rotatsulabs.specty.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NotesTest {
    private val argCount = mapOf(
        NoteKey.ATMOS to 0, NoteKey.LOSSY_CODEC_EDGE to 2, NoteKey.LOSSY_CODEC to 1, NoteKey.NO_DECODER to 0,
        NoteKey.NO_SIGNAL to 0, NoteKey.FAINT_TOP to 0, NoteKey.FULL_BAND to 0, NoteKey.ROLLOFF to 1,
        NoteKey.HARD_CUTOFF to 1, NoteKey.UPSAMPLED to 2, NoteKey.SHARP_CUTOFF to 1, NoteKey.BAND_LIMITED to 1,
        NoteKey.BITS_PADDED to 2, NoteKey.SECTIONS_DISAGREE to 2, NoteKey.TOO_LITTLE_CLEAR to 0, NoteKey.FEW_SECTIONS to 1,
        NoteKey.UPSAMPLE_HOLD to 2, NoteKey.UPSAMPLE_LINE to 2, NoteKey.FLOOR_AT_16BIT to 0, NoteKey.FLOOR_BELOW_16BIT to 0,
        NoteKey.VERDICTS_DIFFER to 2, NoteKey.MORE_TOP_END to 4, NoteKey.B_REACHES_TOP to 1, NoteKey.A_REACHES_TOP to 1,
        NoteKey.MORE_DYNAMIC to 3, NoteKey.LOUDER to 2, NoteKey.MORE_CLIPPING to 1, NoteKey.ALMOST_SAME to 0,
    )

    private fun sample(key: NoteKey): Note {
        val args = (1..argCount.getValue(key)).map { i ->
            if (key == NoteKey.VERDICTS_DIFFER) Verdict.entries[i - 1].name else i.toString()
        }
        return Note(key, args)
    }

    /** Resource name for a note: highlights use hl_, verdict notes use note_. */
    private fun resourceName(key: NoteKey) =
        (if (key >= NoteKey.VERDICTS_DIFFER) "hl_" else "note_") + key.name.lowercase()

    @Test
    fun everyKeyHasAnEnglishSentence() {
        assertEquals(NoteKey.entries.toSet(), argCount.keys)
        for (k in NoteKey.entries) assertTrue(k.name, Notes.english(sample(k)).isNotBlank())
    }

    @Test
    fun everyKeyIsTranslatedWithMatchingArguments() {
        var root: File? = File(System.getProperty("user.dir")!!).absoluteFile
        while (root != null && !File(root, "gradle/libs.versions.toml").exists()) root = root.parentFile
        val res = File(root, "app/src/main/res")
        for (dir in listOf("values", "values-in", "values-zh-rCN")) {
            val xml = File(res, "$dir/strings.xml").readText()
            for (k in NoteKey.entries) {
                val name = resourceName(k)
                val body = Regex("""<(string|plurals) name="$name"[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
                    .find(xml)?.groupValues?.get(2)
                assertTrue("$dir is missing $name", body != null)
                val used = Regex("""%(\d)\$""").findAll(body!!).map { it.groupValues[1].toInt() }.toSet()
                val expected = (1..argCount.getValue(k)).toSet()
                // A language may leave an argument out, but never refer to one that does not exist.
                assertTrue("$dir $name uses $used, only $expected exist", expected.containsAll(used))
                if (dir == "values") assertEquals("$name in English must use every argument", expected, used)
            }
        }
    }
}
