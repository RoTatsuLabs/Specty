package com.rotatsulabs.specty.audio

/**
 * Where the spectrum of an upsampled file stops: at the Nyquist of the source, a little below it because a resampler's
 * filter starts rolling off before it. Only sources of 44.1, 48, 88.2 and 96 kHz are looked for.
 */
object UpsampleEdge {
    class Source(val lowKhz: String, val highKhz: String, val hiRes: Boolean, val nyquistHz: Double)

    private val NYQUISTS = doubleArrayOf(22_050.0, 24_000.0, 44_100.0, 48_000.0)

    /** An edge between this share of the source Nyquist and just above it. */
    private const val FROM = 0.90
    private const val TO = 1.005

    /** The file must reach this far above the source Nyquist, or there is no empty band to see the edge against. */
    private const val ROOM = 1.8

    fun match(edgeHz: Double, sampleRate: Int): Source? {
        for (nyquist in NYQUISTS) {
            if (edgeHz in nyquist * FROM..nyquist * TO && sampleRate / 2.0 >= nyquist * ROOM) {
                return if (nyquist < 40_000.0) Source("44.1", "48", false, nyquist) else Source("88.2", "96", true, nyquist)
            }
        }
        return null
    }
}
