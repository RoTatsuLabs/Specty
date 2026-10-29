package com.rotatsulabs.specty.ui.design.tonal

import androidx.annotation.StringRes
import com.rotatsulabs.specty.core.design.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tonal palettes without any Android dependency. Colours are built in OKLCH and the tone (0 black to 100 white)
 * is the CIE L* of the result's relative luminance, so the usual Material contrast rules (tone distance of 40
 * is about 3:1, 50 about 4.5:1) hold for every hue.
 */
object Oklch {
    private fun srgbToLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    private fun linearToSrgb(c: Double) = if (c <= 0.0031308) c * 12.92 else 1.055 * c.pow(1 / 2.4) - 0.055

    /** Linear sRGB (may fall outside 0..1) for an OKLCH colour. */
    fun toLinear(l: Double, c: Double, hDeg: Double): DoubleArray {
        val h = Math.toRadians(hDeg)
        val a = c * cos(h)
        val b = c * sin(h)
        val l_ = l + 0.3963377774 * a + 0.2158037573 * b
        val m_ = l - 0.1055613458 * a - 0.0638541728 * b
        val s_ = l - 0.0894841775 * a - 1.2914855480 * b
        val lc = l_ * l_ * l_
        val mc = m_ * m_ * m_
        val sc = s_ * s_ * s_
        return doubleArrayOf(
            4.0767416621 * lc - 3.3077115913 * mc + 0.2309699292 * sc,
            -1.2684380046 * lc + 2.6097574011 * mc - 0.3413193965 * sc,
            -0.0041960863 * lc - 0.7034186147 * mc + 1.7076147010 * sc,
        )
    }

    private fun inGamut(rgb: DoubleArray) = rgb.all { it >= -1e-6 && it <= 1.0 + 1e-6 }

    /** Linear sRGB for the colour, pulling chroma down until it fits the sRGB gamut. */
    fun toLinearInGamut(l: Double, c: Double, hDeg: Double): DoubleArray {
        val direct = toLinear(l, c, hDeg)
        if (inGamut(direct)) return direct
        var lo = 0.0
        var hi = c
        repeat(22) {
            val mid = (lo + hi) / 2
            if (inGamut(toLinear(l, mid, hDeg))) lo = mid else hi = mid
        }
        return toLinear(l, lo, hDeg)
    }

    fun relativeLuminance(linear: DoubleArray): Double =
        0.2126 * linear[0].coerceIn(0.0, 1.0) + 0.7152 * linear[1].coerceIn(0.0, 1.0) + 0.0722 * linear[2].coerceIn(0.0, 1.0)

    fun toArgb(linear: DoubleArray): Int {
        fun ch(v: Double) = (linearToSrgb(v.coerceIn(0.0, 1.0)) * 255.0).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(linear[0]) shl 16) or (ch(linear[1]) shl 8) or ch(linear[2])
    }

    /** OKLCH of an ARGB colour as (lightness, chroma, hue in degrees). */
    fun fromArgb(argb: Int): Triple<Double, Double, Double> {
        val r = srgbToLinear((argb shr 16 and 0xFF) / 255.0)
        val g = srgbToLinear((argb shr 8 and 0xFF) / 255.0)
        val b = srgbToLinear((argb and 0xFF) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val ll = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        var h = Math.toDegrees(atan2(bb, a))
        if (h < 0) h += 360.0
        return Triple(ll, sqrt(a * a + bb * bb), h)
    }
}

/** CIE L* (0..100) to relative luminance. */
internal fun toneToLuminance(tone: Double): Double =
    if (tone > 8.0) ((tone + 16.0) / 116.0).pow(3.0) else tone / 903.2962963

/** CIE L* of a relative luminance. */
fun luminanceToTone(y: Double): Double =
    if (y <= 216.0 / 24389.0) y * 903.2962963 else 116.0 * cbrt(y) - 16.0

/** One hue and chroma at every tone. Results are cached per tone. */
class TonalPalette(val hue: Double, val chroma: Double) {
    private val cache = HashMap<Int, Int>()

    /** ARGB colour at [tone] 0..100. */
    fun tone(tone: Int): Int = cache.getOrPut(tone) {
        when {
            tone <= 0 -> 0xFF000000.toInt()
            tone >= 100 -> 0xFFFFFFFF.toInt()
            else -> solve(tone.toDouble())
        }
    }

    private fun solve(tone: Double): Int {
        val target = toneToLuminance(tone)
        var lo = 0.0
        var hi = 1.0
        repeat(34) {
            val mid = (lo + hi) / 2
            val y = Oklch.relativeLuminance(Oklch.toLinearInGamut(mid, chroma, hue))
            if (y < target) lo = mid else hi = mid
        }
        return Oklch.toArgb(Oklch.toLinearInGamut((lo + hi) / 2, chroma, hue))
    }
}

/** Seed colours offered in settings. [hue] is the OKLCH hue in degrees. */
enum class SeedPalette(@param:StringRes val labelRes: Int, val hue: Double, val chroma: Double = 0.15) {
    Violet(R.string.palette_violet, 295.0),
    Azure(R.string.palette_azure, 250.0),
    Teal(R.string.palette_teal, 190.0, 0.12),
    Lime(R.string.palette_lime, 128.0, 0.15),
    Amber(R.string.palette_amber, 75.0, 0.14),
    Rose(R.string.palette_rose, 355.0),
}

/** All colour roles as ARGB ints, ready to map onto a Compose ColorScheme. */
class ColorRoles(
    val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
    val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
    val tertiary: Int, val onTertiary: Int, val tertiaryContainer: Int, val onTertiaryContainer: Int,
    val error: Int, val onError: Int, val errorContainer: Int, val onErrorContainer: Int,
    val background: Int, val onBackground: Int, val surface: Int, val onSurface: Int,
    val surfaceVariant: Int, val onSurfaceVariant: Int, val outline: Int, val outlineVariant: Int,
    val inverseSurface: Int, val inverseOnSurface: Int, val inversePrimary: Int,
    val surfaceDim: Int, val surfaceBright: Int,
    val surfaceContainerLowest: Int, val surfaceContainerLow: Int, val surfaceContainer: Int,
    val surfaceContainerHigh: Int, val surfaceContainerHighest: Int,
)

/** The palettes derived from one seed: a vivid primary, a muted secondary, an analogous-plus-60 tertiary and neutrals. */
class SeedScheme(seed: SeedPalette) {
    val primary = TonalPalette(seed.hue, seed.chroma)
    val secondary = TonalPalette(seed.hue, seed.chroma / 3.0)
    val tertiary = TonalPalette((seed.hue + 60.0) % 360.0, seed.chroma * 0.55)
    val neutral = TonalPalette(seed.hue, 0.012)
    val neutralVariant = TonalPalette(seed.hue, 0.024)
    val error = TonalPalette(27.0, 0.19)

    fun roles(dark: Boolean): ColorRoles = if (dark) {
        ColorRoles(
            primary.tone(80), primary.tone(20), primary.tone(30), primary.tone(90),
            secondary.tone(80), secondary.tone(20), secondary.tone(30), secondary.tone(90),
            tertiary.tone(80), tertiary.tone(20), tertiary.tone(30), tertiary.tone(90),
            error.tone(80), error.tone(20), error.tone(30), error.tone(90),
            neutral.tone(6), neutral.tone(90), neutral.tone(6), neutral.tone(90),
            neutralVariant.tone(30), neutralVariant.tone(80), neutralVariant.tone(60), neutralVariant.tone(30),
            neutral.tone(90), neutral.tone(20), primary.tone(40),
            neutral.tone(6), neutral.tone(24),
            neutral.tone(4), neutral.tone(10), neutral.tone(12), neutral.tone(17), neutral.tone(22),
        )
    } else {
        ColorRoles(
            primary.tone(40), primary.tone(100), primary.tone(90), primary.tone(10),
            secondary.tone(40), secondary.tone(100), secondary.tone(90), secondary.tone(10),
            tertiary.tone(40), tertiary.tone(100), tertiary.tone(90), tertiary.tone(10),
            error.tone(40), error.tone(100), error.tone(90), error.tone(10),
            neutral.tone(98), neutral.tone(10), neutral.tone(98), neutral.tone(10),
            neutralVariant.tone(90), neutralVariant.tone(30), neutralVariant.tone(50), neutralVariant.tone(80),
            neutral.tone(20), neutral.tone(95), primary.tone(80),
            neutral.tone(87), neutral.tone(98),
            neutral.tone(100), neutral.tone(96), neutral.tone(94), neutral.tone(92), neutral.tone(90),
        )
    }
}

/** WCAG contrast ratio between two ARGB colours. */
fun contrastRatio(a: Int, b: Int): Double {
    fun y(c: Int) = Oklch.relativeLuminance(
        doubleArrayOf(
            (c shr 16 and 0xFF) / 255.0, (c shr 8 and 0xFF) / 255.0, (c and 0xFF) / 255.0,
        ).map { v -> if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }.toDoubleArray(),
    )
    val ya = y(a); val yb = y(b)
    val hi = maxOf(ya, yb); val lo = minOf(ya, yb)
    return (hi + 0.05) / (lo + 0.05)
}

internal fun hueDistance(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return if (d > 180.0) 360.0 - d else d
}
