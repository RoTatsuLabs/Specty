package com.rotatsulabs.specty.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The system sans-serif. Android 13+ ships it as a variable font, so every weight used below is drawn by the
 * font itself, and the default family needs no extra font loading at startup.
 */
val SpectyFontFamily: FontFamily = FontFamily.SansSerif

private val base = Typography()

private fun TextStyle.styled(weight: FontWeight, tracking: Float = letterSpacing.value) =
    copy(fontFamily = SpectyFontFamily, fontWeight = weight, letterSpacing = tracking.sp)

/**
 * The Material 3 type scale with expressive proportions: display and headline are heavy and tightly tracked,
 * titles are semibold, body stays calm and labels are semibold with a little air.
 */
val SpectyTypography = Typography(
    displayLarge = base.displayLarge.styled(FontWeight.Bold, -1f),
    displayMedium = base.displayMedium.styled(FontWeight.Bold, -0.75f),
    displaySmall = base.displaySmall.styled(FontWeight.Bold, -0.5f),
    headlineLarge = base.headlineLarge.styled(FontWeight.Bold, -0.25f),
    headlineMedium = base.headlineMedium.styled(FontWeight.SemiBold, -0.1f),
    headlineSmall = base.headlineSmall.styled(FontWeight.SemiBold, 0f),
    titleLarge = base.titleLarge.styled(FontWeight.SemiBold, 0f),
    titleMedium = base.titleMedium.styled(FontWeight.Medium, 0.1f),
    titleSmall = base.titleSmall.styled(FontWeight.Medium, 0.1f),
    bodyLarge = base.bodyLarge.styled(FontWeight.Normal),
    bodyMedium = base.bodyMedium.styled(FontWeight.Normal),
    bodySmall = base.bodySmall.styled(FontWeight.Normal),
    labelLarge = base.labelLarge.styled(FontWeight.Medium, 0.1f),
    labelMedium = base.labelMedium.styled(FontWeight.Medium, 0.4f),
    labelSmall = base.labelSmall.styled(FontWeight.Medium, 0.4f),
)

/** Figures that line up in columns and do not jitter while a value changes: tabular numerals. */
val Typography.numericLarge: TextStyle get() = headlineMedium.copy(fontFeatureSettings = "tnum")
val Typography.numericMedium: TextStyle get() = titleLarge.copy(fontFeatureSettings = "tnum")
val Typography.numericSmall: TextStyle get() = bodyMedium.copy(fontFeatureSettings = "tnum")

// Emphasized variants: the same sizes one or two steps heavier, for the one line on a screen that should lead.
val Typography.displayEmphasized: TextStyle get() = displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-2).sp)
val Typography.headlineEmphasized: TextStyle get() = headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
val Typography.titleEmphasized: TextStyle get() = titleLarge.copy(fontWeight = FontWeight.ExtraBold)
val Typography.labelEmphasized: TextStyle get() = labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
