package com.rotatsulabs.specty.audio

/** Which sentence a [Note] stands for. The UI turns it into text in the app language; [Notes.english] is the fallback. */
enum class NoteKey {
    // Verdict notes.
    ATMOS, LOSSY_CODEC_EDGE, LOSSY_CODEC, NO_DECODER, NO_SIGNAL, FAINT_TOP, FULL_BAND, ROLLOFF,
    HARD_CUTOFF, UPSAMPLED, SHARP_CUTOFF, BAND_LIMITED, BITS_PADDED, SECTIONS_DISAGREE, TOO_LITTLE_CLEAR, FEW_SECTIONS,

    // Compare highlights.
    VERDICTS_DIFFER, MORE_TOP_END, B_REACHES_TOP, A_REACHES_TOP, MORE_DYNAMIC, LOUDER, MORE_CLIPPING, ALMOST_SAME,
}

/** A sentence as data: a key and its values, already formatted (kHz with one decimal, codec names, counts). */
data class Note(val key: NoteKey, val args: List<String>) {
    constructor(key: NoteKey, vararg values: Any) : this(key, values.map { it.toString() })
}

object Notes {
    /** English text of one note. Used by the report export and as the stored-text equivalent in tests. */
    fun english(n: Note): String {
        val a = n.args
        return when (n.key) {
            NoteKey.ATMOS ->
                "Dolby Atmos object audio carried in a Dolby Digital Plus (E-AC-3) stream. The spectrum is not judged for lossy origin."
            NoteKey.LOSSY_CODEC_EDGE -> "${a[0]} is a lossy codec; its encoder low-pass sits near ${a[1]} kHz."
            NoteKey.LOSSY_CODEC -> "${a[0]} is a lossy codec, so this is not a lossless file."
            NoteKey.NO_DECODER -> "This device has no decoder for it, so the spectrum and levels could not be measured."
            NoteKey.NO_SIGNAL -> "There is not enough audible content to judge the spectrum."
            NoteKey.FAINT_TOP ->
                "The top of the spectrum is faint, so quiet content and noise cannot be told apart. " +
                    "A lossy origin is neither shown nor ruled out."
            NoteKey.FULL_BAND -> "Content reaches the top of the spectrum, with no cutoff or roll-off found."
            NoteKey.ROLLOFF ->
                "The spectrum fades out near ${a[0]} kHz without a sharp edge. " +
                    "That can be a mastering filter, an older source or a soft low-pass."
            NoteKey.HARD_CUTOFF ->
                "Hard cutoff near ${a[0]} kHz, typical of an MP3 or AAC low-pass. " +
                    "This file was most likely transcoded from a lossy source."
            NoteKey.UPSAMPLED ->
                "Content stops near ${a[0]} kHz inside a ${a[1]} kHz file. " +
                    "It was most likely upsampled from a 44.1 or 48 kHz source."
            NoteKey.SHARP_CUTOFF ->
                "Sharp cutoff near ${a[0]} kHz. This can come from a high-bitrate lossy source, " +
                    "but some masters are also filtered this way."
            NoteKey.BAND_LIMITED -> "The spectrum is band-limited near ${a[0]} kHz. That may be a mastering filter or an older source."
            NoteKey.BITS_PADDED -> "Only the top ${a[0]} bits carry data, so this looks like ${a[0]}-bit audio stored as ${a[1]}-bit."
            NoteKey.SECTIONS_DISAGREE ->
                "Sections of this track disagree about the top of the spectrum (${a[0]} of ${a[1]} match the average), " +
                    "so no verdict is given."
            NoteKey.TOO_LITTLE_CLEAR ->
                "Too little of the track gives a clear reading of the top of the spectrum to rule out a lossy origin."
            NoteKey.FEW_SECTIONS ->
                "Only ${a[0]} section${if (a[0] == "1") "" else "s"} of the track could be read clearly."

            NoteKey.VERDICTS_DIFFER -> "Verdicts differ: A is ${verdictPhrase(a[0])}, B is ${verdictPhrase(a[1])}."
            NoteKey.MORE_TOP_END -> "${a[0]} keeps ${a[1]} kHz more top end (cutoff ${a[2]} kHz vs ${a[3]} kHz)."
            NoteKey.B_REACHES_TOP -> "B reaches the top of the spectrum; A is cut off near ${a[0]} kHz."
            NoteKey.A_REACHES_TOP -> "A reaches the top of the spectrum; B is cut off near ${a[0]} kHz."
            NoteKey.MORE_DYNAMIC -> "${a[0]} is more dynamic (DR${a[1]} vs DR${a[2]})."
            NoteKey.LOUDER -> "${a[0]} is ${a[1]} LU louder."
            NoteKey.MORE_CLIPPING -> "${a[0]} has more clipping."
            NoteKey.ALMOST_SAME -> "These two files measure almost the same."
        }
    }

    /** Sentences joined the way the stored note used to read. */
    fun english(notes: List<Note>): String = notes.joinToString(" ") { english(it) }

    private fun verdictPhrase(name: String) = when (Verdict.valueOf(name)) {
        Verdict.GENUINE -> "lossless"
        Verdict.HI_RES -> "hi-res lossless"
        Verdict.LOSSY_SOURCE -> "from a lossy source"
        Verdict.POSSIBLE_LOSSY_SOURCE -> "possibly from a lossy source"
        Verdict.UPSAMPLED -> "upsampled"
        Verdict.BAND_LIMITED -> "band-limited"
        Verdict.LOSSY_CODEC -> "a lossy format"
        Verdict.DOLBY_ATMOS -> "Dolby Atmos"
        Verdict.INCONCLUSIVE -> "inconclusive"
    }
}
