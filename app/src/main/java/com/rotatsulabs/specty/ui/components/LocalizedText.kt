package com.rotatsulabs.specty.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.WindowFn
import com.rotatsulabs.specty.audio.AuthenticityReport
import com.rotatsulabs.specty.audio.AnalysisStage
import com.rotatsulabs.specty.audio.BitDepth
import com.rotatsulabs.specty.audio.BitDepthBasis
import com.rotatsulabs.specty.audio.DecodedFormat
import com.rotatsulabs.specty.audio.FailKey
import com.rotatsulabs.specty.audio.Failure
import com.rotatsulabs.specty.audio.Note
import com.rotatsulabs.specty.audio.NoteKey
import com.rotatsulabs.specty.audio.Spectrogram
import com.rotatsulabs.specty.audio.SpectrumShape
import com.rotatsulabs.specty.audio.Verdict
import java.util.Locale

/** Localised counterpart of [Fmt.spectralEdge], which stays English for the exported image. */
@Composable
fun spectralEdge(report: AuthenticityReport): Pair<String, String> {
    val cutoff = report.cutoffHz
    val emptyAbove = report.emptyAboveHz
    return when {
        cutoff != null -> stringResource(R.string.edge_cutoff) to Fmt.kHz(cutoff)
        report.shape == SpectrumShape.GRADUAL_ROLLOFF && emptyAbove != null ->
            stringResource(R.string.edge_rolloff) to stringResource(R.string.edge_empty_above, Fmt.kHz(emptyAbove))
        report.shape == SpectrumShape.FULL_BAND -> stringResource(R.string.edge_cutoff) to stringResource(R.string.edge_none)
        else -> stringResource(R.string.edge_cutoff) to stringResource(R.string.edge_unknown)
    }
}

/** The report's sentences in the app language, joined the way the English note reads. */
@Composable
fun noteText(notes: List<Note>): String {
    val sep = stringResource(R.string.sentence_sep)
    return notes.map { noteText(it) }.joinToString(sep)
}

@Composable
fun noteText(n: Note): String {
    val a = n.args
    return when (n.key) {
        NoteKey.ATMOS -> stringResource(R.string.note_atmos)
        NoteKey.LOSSY_CODEC_EDGE -> stringResource(R.string.note_lossy_codec_edge, a[0], a[1])
        NoteKey.LOSSY_CODEC -> stringResource(R.string.note_lossy_codec, a[0])
        NoteKey.NO_DECODER -> stringResource(R.string.note_no_decoder)
        NoteKey.NO_SIGNAL -> stringResource(R.string.note_no_signal)
        NoteKey.FAINT_TOP -> stringResource(R.string.note_faint_top)
        NoteKey.FULL_BAND -> stringResource(R.string.note_full_band)
        NoteKey.ROLLOFF -> stringResource(R.string.note_rolloff, a[0])
        NoteKey.HARD_CUTOFF -> stringResource(R.string.note_hard_cutoff, a[0])
        NoteKey.UPSAMPLED -> stringResource(R.string.note_upsampled, a[0], a[1])
        NoteKey.SHARP_CUTOFF -> stringResource(R.string.note_sharp_cutoff, a[0])
        NoteKey.BAND_LIMITED -> stringResource(R.string.note_band_limited, a[0])
        NoteKey.BITS_PADDED -> stringResource(R.string.note_bits_padded, a[0], a[1])
        NoteKey.SECTIONS_DISAGREE -> stringResource(R.string.note_sections_disagree, a[0], a[1])
        NoteKey.TOO_LITTLE_CLEAR -> stringResource(R.string.note_too_little_clear)
        NoteKey.FEW_SECTIONS -> {
            val count = a[0].toIntOrNull() ?: 0
            pluralStringResource(R.plurals.note_few_sections, count, count)
        }
        NoteKey.UPSAMPLE_HOLD -> stringResource(R.string.note_upsample_hold, a[0], a[1])
        NoteKey.UPSAMPLE_LINE -> stringResource(R.string.note_upsample_line, a[0], a[1])
        NoteKey.FLOOR_AT_16BIT -> stringResource(R.string.note_floor_at_16bit)
        NoteKey.FLOOR_BELOW_16BIT -> stringResource(R.string.note_floor_below_16bit)
        NoteKey.VERDICTS_DIFFER -> stringResource(R.string.hl_verdicts_differ, verdictPhrase(a[0]), verdictPhrase(a[1]))
        NoteKey.MORE_TOP_END -> stringResource(R.string.hl_more_top_end, a[0], a[1], a[2], a[3])
        NoteKey.B_REACHES_TOP -> stringResource(R.string.hl_b_reaches_top, a[0])
        NoteKey.A_REACHES_TOP -> stringResource(R.string.hl_a_reaches_top, a[0])
        NoteKey.MORE_DYNAMIC -> stringResource(R.string.hl_more_dynamic, a[0], a[1], a[2])
        NoteKey.LOUDER -> stringResource(R.string.hl_louder, a[0], a[1])
        NoteKey.MORE_CLIPPING -> stringResource(R.string.hl_more_clipping, a[0])
        NoteKey.ALMOST_SAME -> stringResource(R.string.hl_almost_same)
    }
}

@Composable
private fun verdictPhrase(name: String): String = stringResource(
    when (Verdict.valueOf(name)) {
        Verdict.GENUINE -> R.string.hl_verdict_genuine
        Verdict.HI_RES -> R.string.hl_verdict_hi_res
        Verdict.LOSSY_SOURCE -> R.string.hl_verdict_lossy_source
        Verdict.POSSIBLE_LOSSY_SOURCE -> R.string.hl_verdict_possible_lossy
        Verdict.UPSAMPLED -> R.string.hl_verdict_upsampled
        Verdict.BAND_LIMITED -> R.string.hl_verdict_band_limited
        Verdict.LOSSY_CODEC -> R.string.hl_verdict_lossy_codec
        Verdict.DOLBY_ATMOS -> R.string.hl_verdict_atmos
        Verdict.INCONCLUSIVE -> R.string.hl_verdict_inconclusive
    },
)

/** Localised counterpart of [BitDepth.label]. */
@Composable
fun effectiveDepthLabel(declared: Int?, effective: Int, basis: BitDepthBasis): String = when {
    BitDepth.isPadded(declared, effective) -> stringResource(R.string.depth_padded, effective, declared ?: effective)
    basis == BitDepthBasis.ALL_BITS -> stringResource(R.string.depth_all, BitDepth.capacity(declared) ?: effective)
    else -> stringResource(R.string.depth_n, effective)
}

/** Localised counterpart of [DecodedFormat.summary]. */
@Composable
fun decodedSummary(format: DecodedFormat): String {
    val layout = when (format.channels) {
        1 -> stringResource(R.string.decoded_mono)
        2 -> stringResource(R.string.decoded_stereo)
        else -> stringResource(R.string.decoded_channels, format.channels)
    }
    return stringResource(R.string.decoded_summary, String.format(Locale.US, "%.1f", format.sampleRate / 1000.0), layout)
}

private val CHANNEL_LABEL = Regex("""Ch (\d+)""")

/** Tab titles for the spectrogram views. Speaker names (L, R, C, LFE) are the same in every language. */
@Composable
fun viewLabels(spectrogram: Spectrogram): List<String> {
    val mix = stringResource(R.string.view_mix)
    val all = stringResource(R.string.view_channels)
    val mono = stringResource(R.string.view_mono)
    val channel = stringResource(R.string.view_channel, "\u0000")
    return spectrogram.displayLabels.map { label ->
        when (label) {
            "Mix" -> mix
            "Channels" -> all
            "Mono" -> mono
            else -> CHANNEL_LABEL.matchEntire(label)?.let { channel.replace("\u0000", it.groupValues[1]) } ?: label
        }
    }
}

@Composable
fun stageText(stage: AnalysisStage): String = stringResource(
    when (stage) {
        AnalysisStage.READING -> R.string.stage_reading
        AnalysisStage.DECODING -> R.string.stage_decoding
        AnalysisStage.ANALYSING -> R.string.stage_analysing
    },
)

/** Why an analysis failed: the app's own wording when it has one, else the system's message, else a plain fallback. */
@Composable
fun failureText(failure: Failure?, detail: String?): String {
    if (failure == null) return detail?.takeIf { it.isNotBlank() } ?: stringResource(R.string.fail_generic)
    val a = failure.args
    return when (failure.key) {
        FailKey.STORAGE_FULL -> stringResource(R.string.fail_storage_full)
        FailKey.FILE_TOO_LONG -> stringResource(R.string.fail_too_long, a[0])
        FailKey.NOT_ENOUGH_STORAGE -> stringResource(R.string.fail_not_enough_storage, a[0])
        FailKey.MORE_AUDIO_THAN_EXPECTED -> stringResource(R.string.fail_more_audio)
        FailKey.NO_AUDIO -> stringResource(R.string.fail_no_audio)
        FailKey.CANNOT_OPEN -> stringResource(R.string.fail_cannot_open)
        FailKey.NOT_AUDIO -> stringResource(R.string.fail_not_audio)
        FailKey.NO_AUDIO_STREAM -> stringResource(R.string.fail_no_stream)
        FailKey.NO_USABLE_FORMAT -> stringResource(R.string.fail_no_format)
        FailKey.DECODE_FAILED -> stringResource(R.string.fail_decode, a[0].trim()).trim()
    }
}

/** The sentence under the window choice in Settings. The text lives in the app, so the audio module stays free of resources. */
val WindowFn.noteRes: Int
    get() = when (this) {
        WindowFn.Hann -> R.string.window_hann_note
        WindowFn.Hamming -> R.string.window_hamming_note
        WindowFn.BlackmanHarris -> R.string.window_blackman_harris_note
    }
