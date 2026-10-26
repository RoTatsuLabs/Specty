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
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.WindowFn
import com.rotatsulabs.specty.ui.AppLanguage
import kotlinx.coroutines.launch
import com.rotatsulabs.specty.ui.components.Reveal
import com.rotatsulabs.specty.ui.design.LocalThemeReveal
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.OptionDropdown
import com.rotatsulabs.specty.ui.design.components.OptionSegmentedControl
import com.rotatsulabs.specty.ui.design.components.SettingBlock
import com.rotatsulabs.specty.ui.design.components.SettingGroup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Close
import com.rotatsulabs.specty.ui.design.components.ListRow
import com.rotatsulabs.specty.ui.design.components.SettingToggleRow
import com.rotatsulabs.specty.ui.design.components.SwatchRow
import com.rotatsulabs.specty.ui.design.components.groupCorners
import com.rotatsulabs.specty.ui.design.tonal.SeedPalette
import com.rotatsulabs.specty.ui.theme.Appearance
import com.rotatsulabs.specty.ui.theme.DYNAMIC_COLOR_SUPPORTED
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
    fftSize: Int,
    onFftSize: (Int) -> Unit,
    windowFn: WindowFn,
    onWindowFn: (WindowFn) -> Unit,
    savedCount: Int,
    onClearCache: () -> Unit,
    developer: Boolean,
    logging: Boolean,
    onLogging: (Boolean) -> Unit,
    onOpenLog: () -> Unit,
    onDeveloperOff: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isDarkFor(appearance)
    // A change of look spreads out from where it was touched. Picking what is already chosen changes nothing.
    val reveal = LocalThemeReveal.current
    fun themed(origin: Offset? = null, change: () -> Unit) {
        if (reveal == null) change() else if (origin != null) reveal.start(origin, change) else reveal.start(change = change)
    }
    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(stringResource(R.string.settings_title), onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Reveal(0) {
                // Dynamic colour needs Android 12, so older versions skip that row and always show the palette.
                val dynamic = appearance.dynamicColor && DYNAMIC_COLOR_SUPPORTED
                val paletteAt = if (DYNAMIC_COLOR_SUPPORTED) 2 else 1
                val rows = 2 + (if (DYNAMIC_COLOR_SUPPORTED) 1 else 0) + (if (dynamic) 0 else 1)
                SettingGroup(stringResource(R.string.settings_appearance)) {
                    SettingBlock(stringResource(R.string.settings_theme), corners = groupCorners(0, rows)) {
                        OptionSegmentedControl(ThemeMode.entries, appearance.mode, { if (it != appearance.mode) themed { onThemeMode(it) } }, { stringResource(it.labelRes) })
                    }
                    if (DYNAMIC_COLOR_SUPPORTED) {
                        SettingToggleRow(
                            title = stringResource(R.string.settings_dynamic_colour),
                            supporting = stringResource(R.string.settings_dynamic_colour_desc),
                            checked = appearance.dynamicColor,
                            onChecked = { checked -> themed { onDynamicColor(checked) } },
                            icon = Icons.Rounded.Palette,
                            corners = groupCorners(1, rows),
                        )
                    }
                    if (!dynamic) {
                        SettingBlock(stringResource(R.string.settings_palette), supporting = stringResource(R.string.settings_palette_desc), corners = groupCorners(paletteAt, rows)) {
                            SwatchRow(
                                appearance.seed, onSeed, dark = dark,
                                onSelectAt = { seed, at -> if (seed != appearance.seed) themed(at) { onSeed(seed) } },
                            )
                        }
                    }
                    SettingToggleRow(
                        title = stringResource(R.string.settings_pure_black),
                        supporting = stringResource(R.string.settings_pure_black_desc),
                        checked = appearance.pureBlack,
                        enabled = dark,
                        onChecked = { checked -> themed { onPureBlack(checked) } },
                        icon = Icons.Rounded.Contrast,
                        corners = groupCorners(rows - 1, rows),
                    )
                }
            }

            Reveal(0) {
                val context = LocalContext.current
                var language by remember { mutableStateOf(AppLanguage.current(context)) }
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                SettingGroup(stringResource(R.string.settings_language)) {
                    SettingBlock(stringResource(R.string.settings_app_language), supporting = stringResource(R.string.settings_language_desc), corners = groupCorners(0, 1)) {
                        OptionDropdown(
                            AppLanguage.Choice.entries, language,
                            { language = it; scope.launch { AppLanguage.change(context, it) } },
                            { stringResource(it.labelRes) },
                        )
                    }
                }
            }

            Reveal(2) {
                SettingGroup(stringResource(R.string.settings_spectrogram)) {
                    SettingBlock(
                        stringResource(R.string.settings_window_size),
                        supporting = stringResource(R.string.settings_window_size_desc),
                        corners = groupCorners(0, 2),
                    ) {
                        OptionSegmentedControl(fftChoices, fftSize, onFftSize, { "$it" })
                    }
                    SettingBlock(stringResource(R.string.settings_window_fn), supporting = stringResource(windowFn.noteRes), corners = groupCorners(1, 2)) {
                        OptionSegmentedControl(WindowFn.entries, windowFn, onWindowFn, { it.label }, scrollable = true)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_window_apply),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Reveal(3) {
                SettingGroup(stringResource(R.string.settings_saved)) {
                    SettingBlock(
                        stringResource(R.string.settings_cache),
                        supporting = if (savedCount == 0) stringResource(R.string.settings_cache_empty) else pluralStringResource(R.plurals.settings_cache_count, savedCount, savedCount),
                        corners = groupCorners(0, 1),
                    ) {
                        ExpressiveButton(stringResource(R.string.settings_clear_saved), onClearCache, kind = ButtonKind.Tonal, enabled = savedCount > 0)
                    }
                }
            }

            if (developer) {
                Reveal(4) {
                    SettingGroup(stringResource(R.string.settings_developer)) {
                        SettingToggleRow(
                            title = stringResource(R.string.settings_logging),
                            supporting = stringResource(R.string.settings_logging_desc),
                            checked = logging,
                            onChecked = onLogging,
                            icon = Icons.Rounded.BugReport,
                            corners = groupCorners(0, 3),
                        )
                        ListRow(
                            title = stringResource(R.string.settings_log),
                            supporting = stringResource(R.string.settings_log_desc),
                            icon = Icons.Rounded.Terminal,
                            corners = groupCorners(1, 3),
                            onClick = onOpenLog,
                        )
                        ListRow(
                            title = stringResource(R.string.settings_dev_off),
                            supporting = stringResource(R.string.settings_dev_off_desc),
                            icon = Icons.Rounded.Close,
                            corners = groupCorners(2, 3),
                            onClick = onDeveloperOff,
                        )
                    }
                }
            }

            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}
