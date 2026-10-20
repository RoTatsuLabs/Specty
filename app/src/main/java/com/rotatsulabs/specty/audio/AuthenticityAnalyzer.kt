package com.rotatsulabs.specty.audio

import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Judges the top of a file's long-term spectrum for signs of a lossy or upsampled origin.
 *
 * A verdict needs positive evidence. A brick-wall cutoff points to a transcode, content that reaches the top of the
 * band points to genuine audio, and anything the measurements cannot separate (silence, too little music, a faint top
 * end, sections that disagree) is reported as inconclusive. All widths are in Hz and all levels are normalised to the
 * fixed [AnalysisVersion.AUTH_FFT] transform, so the outcome does not depend on how the spectrogram is drawn.
 */
object AuthenticityAnalyzer {
    /** Lossless above this sample rate is called Hi-Res. */
    private const val HI_RES_ABOVE_HZ = 48_000
    private const val MIN_DYNAMIC_DB = 25.0
    private const val MIN_CLIFF_DB = 28.0
    private const val PERMANENT_DROP_DB = 20.0

    /** Mean level of the 1.5 to 5 kHz band (dB per bin at [AnalysisVersion.AUTH_FFT]) below which there is nothing to judge. */
    private const val SILENT_REFERENCE_DB = -95.0

    /** The top end this close to the mid band means content reaches the top. */
    private const val FULL_BAND_GAP_DB = 50.0

    /** The top end this far below the mid band is empty. Between the two limits nothing can be said. */
    private const val EMPTY_TOP_GAP_DB = 65.0

    /** An edge this close to Nyquist is the end of the band, not a cutoff. */
    private const val NYQUIST_EDGE = 0.985

    /** Below this the band is too narrow for a check of the top end. */
    private const val MIN_NYQUIST_HZ = 10_000.0

    /** Sections with a clear reading needed before a section-by-section statement is made. */
    private const val MIN_CLEAR_SECTIONS = 3

    /** Below this share of agreeing sections the track gets no verdict. */
    private const val MIXED_SHARE = 0.5

    const val TIMELINE_SEGMENTS = AuthSpectrumAnalyzer.SECTIONS

    /** What the container alone shows for a lossy file whose audio could not be decoded, so nothing was measured. */
    fun unmeasured(info: AudioInfo): AuthenticityReport = AuthenticityReport(
        Verdict.LOSSY_CODEC, null, 0.0,
        listOf(Note(NoteKey.LOSSY_CODEC, Codecs.displayName(info.codec)), Note(NoteKey.NO_DECODER)),
        confidence = 99,
    )

    /** Full judgement from the spectra built by [AuthSpectrumAnalyzer]. */
    fun assess(info: AudioInfo, spectra: AuthSpectra, effectiveBitDepth: Int?): AuthenticityReport {
        // The spectra come from the decoded PCM, whose rate is what the frequency axis means.
        val measured = if (spectra.sampleRate != info.sampleRate) info.copy(sampleRate = spectra.sampleRate) else info
        val base = analyze(measured, spectra.whole, spectra.fftSize, effectiveBitDepth)
        return withInsights(base, measured, spectra.whole, spectra.fftSize, spectra.sections)
    }

    /**
     * Whole-track reading only: the verdict the average spectrum supports before sections are checked.
     * [withInsights] completes it and may withdraw it.
     */
    fun analyze(
        info: AudioInfo,
        averageDb: FloatArray,
        fftSize: Int,
        effectiveBitDepth: Int?,
    ): AuthenticityReport {
        val d = detect(averageDb, info.sampleRate, fftSize)
        val cutoff = d.cutoff
        val cutoffKhz = cutoff?.let { fmt(it / 1000.0) }

        // What the container says does not depend on the audio.
        if (info.isDolbyAtmos) {
            return AuthenticityReport(
                Verdict.DOLBY_ATMOS, cutoff, d.drop,
                listOf(Note(NoteKey.ATMOS)),
                shape = d.shape,
            )
        }
        if (info.isLossy) {
            val name = Codecs.displayName(info.codec)
            val note = if (cutoffKhz != null) Note(NoteKey.LOSSY_CODEC_EDGE, name, cutoffKhz) else Note(NoteKey.LOSSY_CODEC, name)
            return AuthenticityReport(Verdict.LOSSY_CODEC, cutoff, d.drop, listOf(note), shape = d.shape)
        }

        val bitNote = BitDepth.paddingNote(info.bitDepth, effectiveBitDepth)  // empty unless padded
        return when (d.shape) {
            SpectrumShape.NO_SIGNAL -> AuthenticityReport(
                Verdict.INCONCLUSIVE, null, 0.0,
                listOf(Note(NoteKey.NO_SIGNAL)) + bitNote,
                shape = d.shape,
            )
            SpectrumShape.UNCLEAR -> AuthenticityReport(
                Verdict.INCONCLUSIVE, null, 0.0,
                listOf(Note(NoteKey.FAINT_TOP)) + bitNote,
                shape = d.shape,
            )
            SpectrumShape.FULL_BAND -> AuthenticityReport(
                if (info.sampleRate > HI_RES_ABOVE_HZ) Verdict.HI_RES else Verdict.GENUINE, null, 0.0,
                listOf(Note(NoteKey.FULL_BAND)) + bitNote,
                shape = d.shape,
            )
            SpectrumShape.GRADUAL_ROLLOFF -> AuthenticityReport(
                Verdict.BAND_LIMITED, null, 0.0,
                listOf(Note(NoteKey.ROLLOFF, fmt((d.emptyAboveHz ?: 0.0) / 1000.0))) + bitNote,
                emptyAboveHz = d.emptyAboveHz, shape = d.shape,
            )
            SpectrumShape.ABRUPT_CUTOFF -> {
                val edge = cutoff ?: 0.0
                val rateKhz = fmt(info.sampleRate / 1000.0)
                val (verdict, note) = when {
                    edge <= 16_800.0 -> Verdict.LOSSY_SOURCE to Note(NoteKey.HARD_CUTOFF, cutoffKhz.orEmpty())
                    info.sampleRate > 48_000 && edge in 20_500.0..23_000.0 ->
                        Verdict.UPSAMPLED to Note(NoteKey.UPSAMPLED, cutoffKhz.orEmpty(), rateKhz)
                    info.sampleRate <= 48_000 && edge <= 20_800.0 ->
                        Verdict.POSSIBLE_LOSSY_SOURCE to Note(NoteKey.SHARP_CUTOFF, cutoffKhz.orEmpty())
                    else -> Verdict.BAND_LIMITED to Note(NoteKey.BAND_LIMITED, cutoffKhz.orEmpty())
                }
                AuthenticityReport(verdict, cutoff, d.drop, listOf(note) + bitNote, emptyAboveHz = d.emptyAboveHz, shape = d.shape)
            }
        }
    }

    /**
     * Checks the verdict against sections of the track and adds confidence, a per-section cutoff track and an encoder
     * guess. [sections] holds one average spectrum per slice of the track (null for slices without audible content).
     * Sections that cannot be read clearly neither support nor contradict the verdict; they simply are not evidence.
     */
    fun withInsights(
        report: AuthenticityReport,
        info: AudioInfo,
        averageDb: FloatArray,
        fftSize: Int,
        sections: List<FloatArray?>,
    ): AuthenticityReport {
        if (report.verdict == Verdict.INCONCLUSIVE) return report.copy(confidence = 0)
        val whole = detect(averageDb, info.sampleRate, fftSize)
        val cutoffs = FloatArray(sections.size)
        var clear = 0
        var agreeing = 0
        for ((i, spectrum) in sections.withIndex()) {
            if (spectrum == null) continue
            val d = detect(spectrum, info.sampleRate, fftSize)
            d.cutoff?.let { cutoffs[i] = it.toFloat() }
            if (d.shape == SpectrumShape.NO_SIGNAL || d.shape == SpectrumShape.UNCLEAR) continue
            clear++
            val same = when (whole.shape) {
                SpectrumShape.ABRUPT_CUTOFF ->
                    d.shape == SpectrumShape.ABRUPT_CUTOFF && abs((d.cutoff ?: 0.0) - (whole.cutoff ?: 0.0)) <= 1000.0
                else -> d.shape == whole.shape
            }
            if (same) agreeing++
        }
        val withCounts = report.copy(
            segmentCutoffs = cutoffs, emptyAboveHz = whole.emptyAboveHz.takeIf { report.shape.hasEmptyTop() },
            sectionsJudged = clear, sectionsAgreeing = agreeing,
        )

        val fromContainer = report.verdict == Verdict.LOSSY_CODEC || report.verdict == Verdict.DOLBY_ATMOS
        if (!fromContainer) {
            if (clear >= MIN_CLEAR_SECTIONS && agreeing.toDouble() / clear < MIXED_SHARE) {
                return withCounts.copy(
                    verdict = Verdict.INCONCLUSIVE, cutoffHz = null, cliffDropDb = 0.0, confidence = 0, encoderGuess = null,
                    notes = listOf(Note(NoteKey.SECTIONS_DISAGREE, agreeing, clear)),
                )
            }
            if (clear < MIN_CLEAR_SECTIONS && report.shape != SpectrumShape.ABRUPT_CUTOFF) {
                return withCounts.copy(
                    verdict = Verdict.INCONCLUSIVE, cutoffHz = null, cliffDropDb = 0.0, confidence = 0, encoderGuess = null,
                    notes = listOf(Note(NoteKey.TOO_LITTLE_CLEAR)),
                )
            }
        }

        val guess = if (report.shape == SpectrumShape.ABRUPT_CUTOFF && report.verdict != Verdict.UPSAMPLED) {
            report.cutoffHz?.let { encoderGuess(it, info.codec) }
        } else {
            null
        }
        val limited = !fromContainer && clear < MIN_CLEAR_SECTIONS
        return withCounts.copy(
            confidence = confidence(report, whole, clear, agreeing),
            encoderGuess = guess,
            notes = if (limited) report.notes + Note(NoteKey.FEW_SECTIONS, clear) else report.notes,
        )
    }

    private fun SpectrumShape.hasEmptyTop() = this == SpectrumShape.ABRUPT_CUTOFF || this == SpectrumShape.GRADUAL_ROLLOFF

    /**
     * Built from measured evidence only: how strong the signature is and how much of the track shows it. Sections that
     * could not be read add nothing, and a verdict without enough clear sections is held below 50.
     */
    private fun confidence(r: AuthenticityReport, d: Detection, clear: Int, agreeing: Int): Int {
        if (r.verdict == Verdict.LOSSY_CODEC || r.verdict == Verdict.DOLBY_ATMOS) return 99
        val agree = if (clear >= MIN_CLEAR_SECTIONS) agreeing.toDouble() / clear else null
        var c = when (r.shape) {
            SpectrumShape.ABRUPT_CUTOFF ->
                35.0 + ((r.cliffDropDb - MIN_CLIFF_DB) / 32.0 * 25.0).coerceIn(0.0, 25.0) +
                    (if (d.dynamic >= 35.0) 5.0 else 0.0) + 30.0 * (agree ?: 0.0)
            SpectrumShape.FULL_BAND ->
                30.0 + ((FULL_BAND_GAP_DB - d.gap) / FULL_BAND_GAP_DB * 20.0).coerceIn(0.0, 20.0) + 40.0 * (agree ?: 0.0)
            SpectrumShape.GRADUAL_ROLLOFF ->
                25.0 + ((d.gap - EMPTY_TOP_GAP_DB) / 30.0 * 20.0).coerceIn(0.0, 20.0) + 30.0 * (agree ?: 0.0)
            else -> 0.0
        }
        c -= when (r.verdict) {
            Verdict.POSSIBLE_LOSSY_SOURCE -> 12.0
            Verdict.BAND_LIMITED -> 15.0
            else -> 0.0
        }
        if (agree == null) c = min(c, 49.0)
        return c.roundToInt().coerceIn(5, 95)
    }

    /** Typical low-pass frequencies of common encoders; a hint, since presets and encoders vary. */
    fun encoderGuess(cutoffHz: Double, codec: String): String? {
        val khz = cutoffHz / 1000.0
        val family = if (codec.equals("aac", true)) "AAC" else if (codec.equals("opus", true)) "Opus" else "MP3 / AAC"
        return when {
            khz < 10.0 -> null
            khz < 15.2 -> "$family at a low bitrate (about 96 kbps or less)"
            khz < 16.4 -> "$family around 112 to 128 kbps"
            khz < 17.6 -> "$family around 128 to 160 kbps (LAME 128 kbps low-passes near 17 kHz)"
            khz < 19.0 -> "$family around 160 to 192 kbps (LAME V2 is near 18.6 kHz)"
            khz < 20.2 -> "$family around 224 to 256 kbps (LAME V0 is near 19.5 kHz)"
            khz < 21.2 -> "$family at 256 to 320 kbps (LAME 320 kbps is near 20.5 kHz)"
            else -> null
        }
    }

    /** What the search of an average spectrum found. Levels are dB per bin of the spectrum passed in. */
    class Detection(
        /** Mean level of the 1.5 to 5 kHz band. */
        val reference: Double,
        /** Reference minus the level of the top few percent of bins. */
        val dynamic: Double,
        /** Reference minus the level just below Nyquist. */
        val gap: Double,
        val drop: Double,
        val shape: SpectrumShape,
        /** Edge of a steep cutoff, or null. Nyquist is never used as a stand-in. */
        val cutoff: Double?,
        /** Start of the empty top band, set only when the shape has one. */
        val emptyAboveHz: Double?,
    ) {
        val hasCliff: Boolean get() = cutoff != null
    }

    fun detect(averageDb: FloatArray, sampleRate: Int, fftSize: Int): Detection {
        val bins = averageDb.size
        val binHz = sampleRate.toDouble() / fftSize
        val nyquist = sampleRate / 2.0
        val none = Detection(-200.0, 0.0, 0.0, 0.0, SpectrumShape.NO_SIGNAL, null, null)
        if (nyquist < MIN_NYQUIST_HZ || bins < 64) return none

        // Per-bin levels of noise-like signals rise by 3 dB per halving of the transform; this brings them to AUTH_FFT.
        val norm = 10.0 * log10(fftSize.toDouble() / AnalysisVersion.AUTH_FFT)
        val s = smooth(averageDb, max(1, (65.0 / binHz).roundToInt()))

        val k1 = (1500.0 / binHz).toInt().coerceIn(0, bins - 1)
        val k2 = (5000.0 / binHz).toInt().coerceIn(k1 + 1, bins)
        var reference = 0.0
        for (k in k1 until k2) reference += s[k]
        reference /= (k2 - k1)
        if (reference + norm < SILENT_REFERENCE_DB) return Detection(reference, 0.0, 0.0, 0.0, SpectrumShape.NO_SIGNAL, null, null)

        val floorBins = max(8, bins * 3 / 100).coerceAtMost(bins)
        val floor = median(s.copyOfRange(bins - floorBins, bins))
        val dynamic = reference - floor

        val topFrom = (bins * 0.92).toInt()
        val topTo = (bins * 0.99).toInt().coerceAtLeast(topFrom + 8).coerceAtMost(bins)
        val topLevel = median(s.copyOfRange(topFrom, topTo))
        val gap = reference - topLevel

        // A cliff is a steep drop over ~600 Hz after which the spectrum never climbs back up.
        // Notches and gradual roll-off do not qualify.
        val window = max(3, (600.0 / binHz).roundToInt())
        val startK = (4000.0 / binHz).roundToInt()
        val suffixMax = FloatArray(bins)
        run {
            var running = Float.NEGATIVE_INFINITY
            for (k in bins - 1 downTo 0) {
                if (s[k] > running) running = s[k]
                suffixMax[k] = running
            }
        }
        var bestDrop = 0.0
        var bestK = -1
        if (dynamic >= MIN_DYNAMIC_DB && startK + window < bins) {
            for (k in startK until bins - window) {
                val drop = (s[k] - s[k + window]).toDouble()
                if (drop > bestDrop && suffixMax[k + window] <= s[k] - PERMANENT_DROP_DB) {
                    bestDrop = drop
                    bestK = k
                }
            }
        }
        var cutoff: Double? = null
        if (bestDrop >= MIN_CLIFF_DB && bestK >= 0) {
            // Edge = first bin that falls below the midpoint of the drop.
            val mid = (s[bestK] + s[bestK + window]) / 2f
            var edge = bestK
            while (edge < bestK + window && s[edge] > mid) edge++
            val hz = edge * binHz
            if (hz < nyquist * NYQUIST_EDGE) cutoff = hz
        }

        // Highest frequency still above the noise floor: everything past it is empty.
        var emptyAbove: Double? = null
        val threshold = max(floor + 6.0, -110.0 - norm).toFloat()
        var top = bins - 1
        while (top > 0 && s[top] <= threshold) top--
        val topHz = (top + 1) * binHz
        if (top < bins - 1 && topHz < nyquist * 0.97 && topHz > 1000.0) emptyAbove = topHz

        val shape = when {
            cutoff != null -> SpectrumShape.ABRUPT_CUTOFF
            gap <= FULL_BAND_GAP_DB -> SpectrumShape.FULL_BAND
            gap >= EMPTY_TOP_GAP_DB && emptyAbove != null -> SpectrumShape.GRADUAL_ROLLOFF
            else -> SpectrumShape.UNCLEAR
        }
        val hasEmptyTop = shape == SpectrumShape.ABRUPT_CUTOFF || shape == SpectrumShape.GRADUAL_ROLLOFF
        return Detection(
            reference, dynamic, gap,
            if (cutoff != null) bestDrop else 0.0,
            shape, cutoff, emptyAbove.takeIf { hasEmptyTop },
        )
    }

    private fun smooth(values: FloatArray, radius: Int): FloatArray {
        val out = FloatArray(values.size)
        for (i in values.indices) {
            val lo = max(0, i - radius)
            val hi = minOf(values.size - 1, i + radius)
            var sum = 0.0f
            for (j in lo..hi) sum += values[j]
            out[i] = sum / (hi - lo + 1)
        }
        return out
    }

    private fun median(values: FloatArray): Float {
        val sorted = values.copyOf()
        sorted.sort()
        return sorted[sorted.size / 2]
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)
}
