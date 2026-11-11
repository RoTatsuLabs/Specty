package com.rotatsulabs.specty.audio

/** Which sentence a [Note] stands for. The UI turns it into text in the app language; [Notes.english] is the fallback. */
enum class NoteKey {
    // Verdict notes.
    ATMOS, LOSSY_CODEC_EDGE, LOSSY_CODEC, NO_DECODER, NO_SIGNAL, FAINT_TOP, FULL_BAND, ROLLOFF,
    HARD_CUTOFF, UPSAMPLED, SHARP_CUTOFF, BAND_LIMITED, BITS_PADDED, SECTIONS_DISAGREE, TOO_LITTLE_CLEAR, FEW_SECTIONS,
    UPSAMPLE_HOLD, UPSAMPLE_LINE, FLOOR_AT_16BIT, FLOOR_BELOW_16BIT, ROLLOFF_LOW, UPSAMPLE_POSSIBLE, RATE_UNCONFIRMED,

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
            NoteKey.LOSSY_CODEC -> "${a[0]} is a lossy codec: it shrinks the file by dropping detail that is hard to hear."
            NoteKey.NO_DECODER -> "This device has no decoder for it, so the spectrum and levels could not be measured."
            NoteKey.NO_SIGNAL -> "There is not enough audible content to judge the spectrum."
            NoteKey.FAINT_TOP ->
                "The top of the spectrum is faint, so quiet content and noise cannot be told apart. " +
                    "A lossy origin is neither shown nor ruled out."
            NoteKey.FULL_BAND -> "Content reaches the top of the spectrum, with no cutoff or roll-off found."
            NoteKey.ROLLOFF ->
                "The spectrum fades out gradually above about ${a[0]} kHz, with no sharp edge. " +
                    "This is common in mastering filters, older recordings and soft low-pass filters, " +
                    "and it does not by itself show a lossy origin."
            NoteKey.ROLLOFF_LOW ->
                "The band ends lower than most modern masters. Lossy encoding, tape and vintage transfers can all do this, " +
                    "so the cause stays open."
            NoteKey.HARD_CUTOFF ->
                "Sharp cutoff near ${a[0]} kHz, with more than one sign of an encoder low-pass: a steep edge, silence above it, " +
                    "or the same position all through the track. The file was most likely transcoded from a lossy source."
            NoteKey.UPSAMPLED ->
                "Content stops near ${a[0]} kHz inside a ${a[1]} kHz file. " +
                    "It was most likely upsampled from a 44.1 or 48 kHz source."
            NoteKey.SHARP_CUTOFF ->
                "Sharp cutoff near ${a[0]} kHz. A lossy source can leave an edge like this, but some masters are filtered " +
                    "the same way, so it is not conclusive."
            NoteKey.BAND_LIMITED ->
                "The spectrum ends at a sharp edge near ${a[0]} kHz. Nothing else points to a lossy or upsampled source, " +
                    "and mastering or recording filters can leave an edge like this."
            NoteKey.UPSAMPLE_POSSIBLE ->
                "The spectrum ends near ${a[0]} kHz, where a 44.1 or 48 kHz source would end inside this ${a[1]} kHz file. " +
                    "Nothing else confirms resampling, so this is a lead, not a finding."
            NoteKey.RATE_UNCONFIRMED ->
                "The sample rate is above 48 kHz, but the samples carry no more than CD-level detail, " +
                    "so the file is not confirmed as Hi-Res."
            NoteKey.BITS_PADDED -> "Only the top ${a[0]} bits carry data, so this looks like ${a[0]}-bit audio stored as ${a[1]}-bit."
            NoteKey.SECTIONS_DISAGREE ->
                "Sections of this track disagree about the top of the spectrum (${a[0]} of ${a[1]} match the average), " +
                    "so no verdict is given."
            NoteKey.TOO_LITTLE_CLEAR ->
                "Too little of the track gives a clear reading of the top of the spectrum to rule out a lossy origin."
            NoteKey.FEW_SECTIONS ->
                "Only ${a[0]} section${if (a[0] == "1") "" else "s"} of the track could be read clearly."
            NoteKey.UPSAMPLE_HOLD ->
                "Every sample is repeated ${a[0]} times, the mark of a ${a[1]} kHz source stretched to this rate by sample-and-hold. " +
                    "This is upsampled audio."
            NoteKey.UPSAMPLE_LINE ->
                "The samples between every ${a[0]}th one lie on straight lines, the mark of a ${a[1]} kHz source stretched to this rate " +
                    "by linear interpolation. This is upsampled audio."
            NoteKey.FLOOR_AT_16BIT ->
                "The quietest passages sit at the noise level of 16-bit audio, which fits a CD-resolution source."
            NoteKey.FLOOR_BELOW_16BIT ->
                "The quietest passages are quieter than 16-bit noise, so the file holds more than CD resolution."

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
        Verdict.BAND_LIMITED -> "sharply band-limited"
        Verdict.ROLLOFF -> "gradually rolling off"
        Verdict.LOSSY_CODEC -> "a lossy format"
        Verdict.DOLBY_ATMOS -> "Dolby Atmos"
        Verdict.INCONCLUSIVE -> "inconclusive"
    }
}
