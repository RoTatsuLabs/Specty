package com.rotatsulabs.specty.ui.screens

import com.rotatsulabs.specty.audio.Verdict

/**
 * What a library track can be filtered on: the kind of result it has, or none yet. A track belongs to exactly one
 * kind, and the library shows a track when its kind is among the chosen ones (nothing chosen shows everything).
 */
enum class ResultFilter {
    Unchecked, Lossless, HiRes, NeedsLook, LossyFormat, Atmos, Unclear;

    companion object {
        /** The kind for a saved verdict, or [Unchecked] when the track has no saved result. */
        fun of(verdict: Verdict?): ResultFilter = when (verdict) {
            null -> Unchecked
            Verdict.GENUINE -> Lossless
            Verdict.HI_RES -> HiRes
            // The same three verdicts the library scan reports as "need a look".
            Verdict.LOSSY_SOURCE, Verdict.UPSAMPLED, Verdict.POSSIBLE_LOSSY_SOURCE -> NeedsLook
            Verdict.LOSSY_CODEC -> LossyFormat
            Verdict.DOLBY_ATMOS -> Atmos
            Verdict.BAND_LIMITED, Verdict.INCONCLUSIVE -> Unclear
        }

        /** Results from the ones worth a look down to tracks not checked yet; the order of the Result sort and summaries. */
        val ORDER = listOf(NeedsLook, LossyFormat, Unclear, Lossless, HiRes, Atmos, Unchecked)

        /** The kinds present in [verdicts] with their counts, in [ORDER]. Kinds with no track are left out. */
        fun summary(verdicts: Collection<Verdict?>): List<Pair<ResultFilter, Int>> {
            val counts = counts(verdicts)
            return ORDER.mapNotNull { kind -> counts.getValue(kind).takeIf { it > 0 }?.let { kind to it } }
        }

        fun counts(verdicts: Collection<Verdict?>): Map<ResultFilter, Int> {
            val out = entries.associateWith { 0 }.toMutableMap()
            for (v in verdicts) out[of(v)] = out.getValue(of(v)) + 1
            return out
        }

        /** A selection kept as bits, so it survives rotation and process death as a plain Int. */
        fun fromMask(mask: Int): Set<ResultFilter> = entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()

        fun toMask(selected: Set<ResultFilter>): Int = selected.fold(0) { acc, f -> acc or (1 shl f.ordinal) }
    }
}

/** True when the track should show: nothing chosen means no filtering. */
fun Set<ResultFilter>.accepts(verdict: Verdict?): Boolean = isEmpty() || ResultFilter.of(verdict) in this
