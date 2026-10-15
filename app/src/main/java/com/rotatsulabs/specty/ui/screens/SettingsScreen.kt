package com.rotatsulabs.specty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.BuildConfig
import com.rotatsulabs.specty.audio.WindowFn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.rotatsulabs.specty.ui.components.Reveal
import com.rotatsulabs.specty.ui.components.pressScale
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
    onPureBlack: (Boolean) -> Unit,
    showMetadata: Boolean,
    onShowMetadata: (Boolean) -> Unit,
    fftSize: Int,
    onFftSize: (Int) -> Unit,
    windowFn: WindowFn,
    onWindowFn: (WindowFn) -> Unit,
    savedCount: Int,
    onClearCache: () -> Unit,
    onReplayIntro: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backSource = remember { MutableInteractionSource() }
            FilledTonalIconButton(onClick = onBack, interactionSource = backSource, modifier = Modifier.pressScale(backSource)) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup("Appearance", 0) {
                Text("Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = appearance.mode == mode,
                            onClick = { onThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            label = { Text(mode.label) },
                        )
                    }
                }
                SwitchRow(
                    title = "Dynamic colour",
                    subtitle = "Use colours from your wallpaper. Off uses the default Material palette.",
                    checked = appearance.dynamicColor,
                    onChange = onDynamicColor,
                )
                SwitchRow(
                    title = "Pure black",
                    subtitle = "True black backgrounds in dark mode, easier on OLED screens.",
                    checked = appearance.pureBlack,
                    enabled = isDarkFor(appearance),
                    onChange = onPureBlack,
                )
            }

            SettingsGroup("Results", 1) {
                SwitchRow(
                    title = "Show tags and metadata",
                    subtitle = "Adds a read-only card with the file's tags, including custom and unfamiliar ones. Lyrics are never shown.",
                    checked = showMetadata,
                    onChange = onShowMetadata,
                )
            }

            SettingsGroup("Spectrogram", 2) {
                Text("Window size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Larger windows resolve frequency more finely; smaller ones resolve time more finely.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    fftChoices.forEachIndexed { index, size ->
                        SegmentedButton(
                            selected = fftSize == size,
                            onClick = { onFftSize(size) },
                            shape = SegmentedButtonDefaults.itemShape(index, fftChoices.size),
                            label = { Text("$size") },
                        )
                    }
                }
                Text("Window function", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    WindowFn.entries.forEachIndexed { index, w ->
                        SegmentedButton(
                            selected = windowFn == w,
                            onClick = { onWindowFn(w) },
                            shape = SegmentedButtonDefaults.itemShape(index, WindowFn.entries.size),
                            label = { Text(w.label, maxLines = 1) },
                        )
                    }
                }
                Text(windowFn.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Applies to the next file you analyse. Files already checked with another size or window are analysed again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsGroup("Saved results", 3) {
                Text(
                    if (savedCount == 0) "No saved results yet." else "$savedCount analyses saved on this device, so files you already checked open instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onClearCache, enabled = savedCount > 0) { Text("Clear saved results") }
            }

            SettingsGroup("About", 4) {
                Text("Specty ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Free software under the GNU General Public License v3.0. Decoding by FFmpeg through a maintained ffmpeg-kit fork. " +
                        "All analysis runs on your device and nothing is uploaded.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onReplayIntro) { Text("Show the intro again") }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f),
            )
        }
        Spacer(Modifier.padding(start = 16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SettingsGroup(title: String, index: Int, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Reveal(index) {
    Surface(
        onClick = {},
        interactionSource = source,
        modifier = Modifier.fillMaxWidth().pressScale(source, 0.985f),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
    }
}
