package com.rotatsulabs.specty.ui.theme

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pureBlack: Boolean = false,
)
