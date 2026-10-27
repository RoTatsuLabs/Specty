package com.rotatsulabs.specty.ui.spectrogram

/** The shape of the exported image. Every export of one shape has the same pixel size. */
enum class ReportAspect(val width: Int, val height: Int, val label: String) {
    Portrait(1080, 1920, "9:16"),
    Square(1080, 1080, "1:1"),
    Landscape(1920, 1080, "16:9"),
}

/** The blocks of the report that can be switched off. The title and date are always drawn. */
enum class ReportSection { Details, Verdict, KeyLevels, Measurements, Loudness, Spectrogram, Footer }

/** What the exported image shows and how it is shaped. Kept as two ints, so it fits in preferences. */
data class ReportOptions(
    val aspect: ReportAspect = ReportAspect.Portrait,
    val sections: Set<ReportSection> = DEFAULT_SECTIONS,
) {
    fun has(section: ReportSection) = section in sections

    fun toggled(section: ReportSection, on: Boolean) = copy(sections = if (on) sections + section else sections - section)

    fun sectionMask(): Int = sections.fold(0) { mask, s -> mask or (1 shl s.ordinal) }

    companion object {
        /** Everything but the loudness graph, which most reports do without. */
        val DEFAULT_SECTIONS: Set<ReportSection> = ReportSection.entries.toSet() - ReportSection.Loudness

        /** [mask] below zero means nothing was saved yet. A saved mask of zero is valid: only the title is drawn. */
        fun of(aspectIndex: Int, mask: Int): ReportOptions = ReportOptions(
            ReportAspect.entries[aspectIndex.coerceIn(0, ReportAspect.entries.lastIndex)],
            if (mask < 0) DEFAULT_SECTIONS else ReportSection.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet(),
        )
    }
}

/** Picks the scale a report is drawn at, so every section fits the canvas without being cut off. */
object ReportFit {
    const val MIN = 0.3f
    const val MAX = 1.25f

    /**
     * The largest scale in [MIN]..[MAX] for which [measure] (the height drawn at that scale, which never shrinks as the
     * scale grows) still fits in [available]. When even [MIN] does not fit, [MIN] is returned.
     */
    fun scale(available: Float, measure: (Float) -> Float): Float {
        if (measure(MAX) <= available) return MAX
        if (measure(MIN) > available) return MIN
        var lo = MIN
        var hi = MAX
        repeat(14) {
            val mid = (lo + hi) / 2f
            if (measure(mid) <= available) lo = mid else hi = mid
        }
        return lo
    }

    /** The gap added between blocks when there is room left over, never more than [cap]. */
    fun extraGap(room: Float, blocks: Int, cap: Float): Float =
        if (blocks < 2 || room <= 0f) 0f else (room / (blocks - 1)).coerceAtMost(cap)
}
