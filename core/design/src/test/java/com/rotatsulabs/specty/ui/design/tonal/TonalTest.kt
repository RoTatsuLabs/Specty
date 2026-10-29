package com.rotatsulabs.specty.ui.design.tonal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

class TonalTest {
    @Test
    fun oklchRoundTripsThroughArgb() {
        for (argb in intArrayOf(0xFF3366CC.toInt(), 0xFFE8A33D.toInt(), 0xFF808080.toInt(), 0xFF101010.toInt())) {
            val (l, c, h) = Oklch.fromArgb(argb)
            val back = Oklch.toArgb(
                Oklch.toLinearInGamut(l, c, h),
            )
            for (shift in intArrayOf(16, 8, 0)) {
                assertTrue("$argb vs $back", abs(((argb shr shift) and 0xFF) - ((back shr shift) and 0xFF)) <= 1)
            }
        }
    }

    @Test
    fun toneMatchesLuminanceForEveryHue() {
        for (hue in 0 until 360 step 30) {
            val p = TonalPalette(hue.toDouble(), 0.15)
            var previous = -1.0
            for (tone in intArrayOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 95)) {
                val argb = p.tone(tone)
                val lin = doubleArrayOf(
                    srgbLinear((argb shr 16 and 0xFF) / 255.0), srgbLinear((argb shr 8 and 0xFF) / 255.0), srgbLinear((argb and 0xFF) / 255.0),
                )
                val y = 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
                val measured = luminanceToTone(y)
                assertTrue("hue $hue tone $tone measured $measured", abs(measured - tone) < 1.5)
                assertTrue("tones must rise", measured > previous)
                previous = measured
            }
            assertEquals(0xFF000000.toInt(), p.tone(0))
            assertEquals(0xFFFFFFFF.toInt(), p.tone(100))
        }
    }

    private fun srgbLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    @Test
    fun schemesMeetContrastForEverySeed() {
        for (seed in SeedPalette.entries) {
            val scheme = SeedScheme(seed)
            for (dark in listOf(false, true)) {
                val r = scheme.roles(dark)
                fun check(name: String, fg: Int, bg: Int, min: Double) {
                    val c = contrastRatio(fg, bg)
                    assertTrue("$seed dark=$dark $name contrast $c", c >= min)
                }
                check("primary", r.onPrimary, r.primary, 4.5)
                check("primaryContainer", r.onPrimaryContainer, r.primaryContainer, 4.5)
                check("secondaryContainer", r.onSecondaryContainer, r.secondaryContainer, 4.5)
                check("tertiaryContainer", r.onTertiaryContainer, r.tertiaryContainer, 4.5)
                check("errorContainer", r.onErrorContainer, r.errorContainer, 4.5)
                check("surface", r.onSurface, r.surface, 7.0)
                check("surfaceVariant", r.onSurfaceVariant, r.surfaceVariant, 4.5)
                check("surfaceContainerHighest", r.onSurface, r.surfaceContainerHighest, 7.0)
            }
        }
    }
}
