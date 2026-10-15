package com.rotatsulabs.specty.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.rotatsulabs.specty.ui.design.SpectyMaterialShapes
import com.rotatsulabs.specty.ui.design.SpectyTypography
import com.rotatsulabs.specty.ui.design.tonal.ColorRoles
import com.rotatsulabs.specty.ui.design.tonal.SeedScheme

@Composable
fun isDarkFor(appearance: Appearance): Boolean = when (appearance.mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

private fun ColorScheme.asPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF151515),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF282828),
)

/** Material 3 with optional wallpaper-derived dynamic colour (minSdk 33 guarantees support). */
@Composable
fun SpectyTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isDarkFor(appearance)
    var scheme = when {
        appearance.dynamicColor -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> SeedScheme(appearance.seed).roles(dark).toColorScheme(dark)
    }
    if (dark && appearance.pureBlack) scheme = scheme.asPureBlack()
    MaterialTheme(colorScheme = scheme, typography = SpectyTypography, shapes = SpectyMaterialShapes, content = content)
}

private fun ColorRoles.toColorScheme(dark: Boolean): ColorScheme {
    fun c(v: Int) = Color(v)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(primary), onPrimary = c(onPrimary), primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        secondary = c(secondary), onSecondary = c(onSecondary), secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(onTertiary), tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        error = c(error), onError = c(onError), errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
        background = c(background), onBackground = c(onBackground), surface = c(surface), onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
        outline = c(outline), outlineVariant = c(outlineVariant),
        inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface), inversePrimary = c(inversePrimary),
        surfaceDim = c(surfaceDim), surfaceBright = c(surfaceBright),
        surfaceContainerLowest = c(surfaceContainerLowest), surfaceContainerLow = c(surfaceContainerLow),
        surfaceContainer = c(surfaceContainer), surfaceContainerHigh = c(surfaceContainerHigh),
        surfaceContainerHighest = c(surfaceContainerHighest),
    )
}
