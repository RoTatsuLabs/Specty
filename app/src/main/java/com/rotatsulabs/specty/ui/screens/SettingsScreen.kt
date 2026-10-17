package com.rotatsulabs.specty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.audio.WindowFn
import com.rotatsulabs.specty.ui.components.Reveal
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.OptionSegmentedControl
import com.rotatsulabs.specty.ui.design.components.SettingBlock
import com.rotatsulabs.specty.ui.design.components.SettingGroup
import com.rotatsulabs.specty.ui.design.components.SettingToggleRow
import com.rotatsulabs.specty.ui.design.components.SwatchRow
import com.rotatsulabs.specty.ui.design.components.groupCorners
import com.rotatsulabs.specty.ui.design.tonal.SeedPalette
import com.rotatsulabs.specty.ui.theme.Appearance
import com.rotatsulabs.specty.ui.theme.ThemeMode
import com.rotatsulabs.specty.ui.theme.isDarkFor

private val fftChoices = listOf(1024, 2048, 4096)

/** Full-screen settings, opened from the overflow menu. */
@Composable
fun SettingsScreen(
    appearance: Appearance,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSeed: (SeedPalette) -> Unit,
    onPureBlack: (Boolean) -> Unit,
    showMetadata: Boolean,
    onShowMetadata: (Boolean) -> Unit,
    soundEnabled: Boolean,
    onSound: (Boolean) -> Unit,
    fftSize: Int,
    onFftSize: (Int) -> Unit,
    windowFn: WindowFn,
    onWindowFn: (WindowFn) -> Unit,
    savedCount: Int,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isDarkFor(appearance)
    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar("Settings", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Reveal(0) {
                SettingGroup("Appearance") {
                    SettingBlock("Theme", corners = groupCorners(0, if (appearance.dynamicColor) 3 else 4)) {
                        OptionSegmentedControl(ThemeMode.entries, appearance.mode, onThemeMode, { it.label })
                    }
                    SettingToggleRow(
                        title = "Dynamic colour",
                        supporting = "Use colours from your wallpaper. Off lets you pick a tonal palette.",
                        checked = appearance.dynamicColor,
                        onChecked = onDynamicColor,
                        icon = Icons.Rounded.Palette,
                        corners = groupCorners(1, if (appearance.dynamicColor) 3 else 4),
                    )
                    if (!appearance.dynamicColor) {
                        SettingBlock("Tonal palette", supporting = "Every colour in the app is derived from this one.", corners = groupCorners(2, 4)) {
                            SwatchRow(appearance.seed, onSeed, dark = dark)
                        }
                    }
                    SettingToggleRow(
                        title = "Pure black",
                        supporting = "True black backgrounds in dark mode, easier on OLED screens.",
                        checked = appearance.pureBlack,
                        enabled = dark,
                        onChecked = onPureBlack,
                        icon = Icons.Rounded.Contrast,
                        corners = groupCorners(if (appearance.dynamicColor) 2 else 3, if (appearance.dynamicColor) 3 else 4),
                    )
                }
            }

            Reveal(1) {
                SettingGroup("Results") {
                    SettingToggleRow(
                        title = "Show tags and metadata",
                        supporting = "Adds a read-only card with the file's tags, including custom and unfamiliar ones. Lyrics are never shown.",
                        checked = showMetadata,
                        onChecked = onShowMetadata,
                        icon = Icons.Rounded.Sell,
                        corners = groupCorners(0, 2),
                    )
                    SettingToggleRow(
                        title = "Sound effects",
                        supporting = "Soft clicks and chimes for taps, queued files and finished analyses. Follows your system sound volume.",
                        checked = soundEnabled,
                        onChecked = onSound,
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        corners = groupCorners(1, 2),
                    )
                }
            }

            Reveal(2) {
                SettingGroup("Spectrogram") {
                    SettingBlock(
                        "Window size",
                        supporting = "Larger windows resolve frequency more finely; smaller ones resolve time more finely.",
                        corners = groupCorners(0, 2),
                    ) {
                        OptionSegmentedControl(fftChoices, fftSize, onFftSize, { "$it" })
                    }
                    SettingBlock("Window function", supporting = windowFn.note, corners = groupCorners(1, 2)) {
                        OptionSegmentedControl(WindowFn.entries, windowFn, onWindowFn, { it.label }, scrollable = true)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Applies to the next file you analyse. Files already checked with another size or window are analysed again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Reveal(3) {
                SettingGroup("Saved results") {
                    SettingBlock(
                        "Cache",
                        supporting = if (savedCount == 0) "No saved results yet." else "$savedCount analyses saved on this device, so files you already checked open instantly.",
                        corners = groupCorners(0, 1),
                    ) {
                        ExpressiveButton("Clear saved results", onClearCache, kind = ButtonKind.Tonal, enabled = savedCount > 0)
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}
