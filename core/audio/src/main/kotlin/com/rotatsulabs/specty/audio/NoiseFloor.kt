package com.rotatsulabs.specty.audio

import kotlin.math.log10
import kotlin.math.pow

/**
 * Compares the noise floor of the quietest passages with flat 16-bit quantisation noise. A floor below it means the
 * file holds resolution a 16-bit copy would lose; a floor at it fits a CD-resolution source.
 *
 * Adapted from the Hi-Res checks in Mornye (https://github.com/mornyeapp/Mornye,
 * `rust_backend/crates/core/src/media/hires/evidence.rs`), LGPL-3.0. Ported to Kotlin and combined with Specty under
 * GPL-3.0. See About > Open source licences.
 */
object NoiseFloor {
    enum class Level { BELOW_16BIT, AT_16BIT, MASKED }

    class Reading(val level: Level, val vs16BitDb: Double)

    /** The floor is read over this band, which a noise-shaped dither cannot lower. */
    const val BAND_LOW_HZ = 500.0
    const val BAND_HIGH_HZ = 20_000.0

    /** Share of the non-silent frames, quietest first, the floor is read in. */
    const val QUIET_FRACTION = 0.1

    private const val BELOW_16BIT_DB = -6.0

    /**
     * Above this the floor is too high for 16-bit quantisation noise: it is analog noise or quiet music. Flat 16-bit
     * dither reads about +6 dB here (+4.8 dB by theory) and two stages of it about +8 dB, so a 24-bit recording with
     * an analog floor of +10 dB or more is no longer taken for 16-bit audio.
     */
    private const val MASKED_DB = 9.5

    /**
     * [floorVariance] is the white-noise equivalent power per channel in full-scale units (+-1.0), as estimated from
     * the median bin power of the quiet frames. Null or not positive means no frame qualified.
     * [bandShare] is the share of the band the noise fills when it stops at the Nyquist of a lower-rate source; the
     * estimate spreads it over the whole band, so it reads that much too high.
     */
    fun classify(floorVariance: Double?, bandShare: Double = 1.0): Reading? {
        if (floorVariance == null || floorVariance.isNaN() || floorVariance <= 0.0) return null
        val lsb = 2.0.pow(-15)
        val reference = lsb * lsb / 12.0
        val db = 10.0 * log10(floorVariance * bandShare / reference)
        val level = when {
            db < BELOW_16BIT_DB -> Level.BELOW_16BIT
            db > MASKED_DB -> Level.MASKED
            else -> Level.AT_16BIT
        }
        return Reading(level, db)
    }
}
