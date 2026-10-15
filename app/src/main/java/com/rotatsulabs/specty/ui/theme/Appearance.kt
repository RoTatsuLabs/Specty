package com.rotatsulabs.specty.ui.theme

import com.rotatsulabs.specty.ui.design.tonal.SeedPalette

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
    /** Used when dynamic colour is off. */
    val seed: SeedPalette = SeedPalette.Violet,
)
