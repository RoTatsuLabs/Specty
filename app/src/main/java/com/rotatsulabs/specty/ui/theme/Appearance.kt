package com.rotatsulabs.specty.ui.theme

import androidx.annotation.StringRes
import com.rotatsulabs.specty.R
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
)
