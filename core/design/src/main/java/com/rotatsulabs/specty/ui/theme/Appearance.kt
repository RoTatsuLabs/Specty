package com.rotatsulabs.specty.ui.theme

import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.rotatsulabs.specty.core.design.R
import com.rotatsulabs.specty.ui.design.tonal.SeedPalette

enum class ThemeMode(@param:StringRes val labelRes: Int) {
    System(R.string.theme_system),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark),
}

data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    /** Used when dynamic colour is off. */
    val seed: SeedPalette = SeedPalette.Violet,
    /** Fewer slides, and no stretch in the nav bar. */
    val reduceMotion: Boolean = false,
)

/** Height of the floating nav bar, so a tab's lists can end clear of it while still scrolling behind it. */
val LocalNavBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** True when the person asked for less motion; read by the nav bar and the screen transitions. */
val LocalReducedMotion = androidx.compose.runtime.compositionLocalOf { false }

