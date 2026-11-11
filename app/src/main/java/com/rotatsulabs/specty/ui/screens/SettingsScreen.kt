package com.rotatsulabs.specty.ui.screens

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import com.rotatsulabs.specty.BuildConfig
import com.rotatsulabs.specty.ui.design.TermIcons
import androidx.compose.foundation.layout.Box
import com.rotatsulabs.specty.ui.design.underlay
import com.rotatsulabs.specty.ui.design.OverlayScreen
import com.rotatsulabs.specty.ui.design.OverlayDepth
import com.rotatsulabs.specty.ui.design.BackGesture
import com.rotatsulabs.specty.ui.components.withoutExplicitMark
import kotlin.math.roundToInt
import com.rotatsulabs.specty.ui.components.CoverImage
import com.rotatsulabs.specty.data.Track
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.ViewAgenda
import com.rotatsulabs.specty.ui.CoverShape
import com.rotatsulabs.specty.ui.toShape
import com.rotatsulabs.specty.ui.RowDensity
import com.rotatsulabs.specty.ui.RowStyle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramPalette
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramRenderer
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramSample
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.design.components.verdictStyle
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedContent
import com.rotatsulabs.specty.ui.design.components.SegmentedControl
import com.rotatsulabs.specty.ui.design.components.Segment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.ui.graphics.vector.ImageVector
import com.rotatsulabs.specty.ui.design.Springy
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
import com.rotatsulabs.specty.ui.components.noteRes
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

/** The settings tab. */
@Composable
fun SettingsScreen(
    appearance: Appearance,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSeed: (SeedPalette) -> Unit,
    onPureBlack: (Boolean) -> Unit,
    onReduceMotion: (Boolean) -> Unit,
    rowStyle: RowStyle,
    onDensity: (RowDensity) -> Unit,
    onCoverShape: (CoverShape) -> Unit,
    navLabels: com.rotatsulabs.specty.ui.design.components.NavLabels,
    onNavLabels: (com.rotatsulabs.specty.ui.design.components.NavLabels) -> Unit,
    onShowResult: (Boolean) -> Unit,
    onShowFormat: (Boolean) -> Unit,
    onShowFolder: (Boolean) -> Unit,
    keepAwake: Boolean,
    onKeepAwake: (Boolean) -> Unit,
    autoScan: Boolean,
    onAutoScan: (Boolean) -> Unit,
    minSeconds: Int,
    onMinSeconds: (Int) -> Unit,
    fftSize: Int,
    onFftSize: (Int) -> Unit,
    windowFn: WindowFn,
    onWindowFn: (WindowFn) -> Unit,
    savedCount: Int,
    previewTracks: List<Track>,
    onClearCache: () -> Unit,
    developer: Boolean,
    logging: Boolean,
    onLogging: (Boolean) -> Unit,
    onOpenLog: () -> Unit,
    onDeveloperOff: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFolders: () -> Unit,
    /** Which category is open, or null for the list. Held by the caller so a screen opened over settings returns to it. */
    section: String?,
    onSection: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isDarkFor(appearance)
    // A change of look spreads out from where it was touched. Picking what is already chosen changes nothing.
    val reveal = LocalThemeReveal.current
    fun themed(origin: Offset? = null, change: () -> Unit) {
        if (reveal == null) change() else if (origin != null) reveal.start(origin, change) else reveal.start(change = change)
    }
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppLanguage.current(context)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val open = section?.let { name -> Section.entries.firstOrNull { it.name == name } }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(
            stringResource(open?.titleRes ?: R.string.settings_title),
            onBack = { if (open != null) onSection(null) else onBack() },
        )

        AnimatedContent(
            targetState = open,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = targetState != null
                Springy.enterX(forward) togetherWith Springy.exitX(forward)
            },
            label = "settingsSection",
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when (current) {
                    null -> {
                        val dynamic = appearance.dynamicColor && DYNAMIC_COLOR_SUPPORTED
                        val rows = Section.entries.filter { it != Section.Developer || developer }
                        SettingsSearchField(query, { query = it })
                        val needle = query.trim()
                        if (needle.isNotEmpty()) {
                            val hits = SETTING_HITS.filter { (it.section != Section.Developer || developer) }.map { hit ->
                                Triple(hit, stringResource(hit.titleRes), hit.descRes?.let { stringResource(it) })
                            }.filter { (_, title, desc) -> title.contains(needle, ignoreCase = true) || (desc?.contains(needle, ignoreCase = true) == true) }
                            if (hits.isEmpty()) {
                                Text(
                                    stringResource(R.string.settings_search_none),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                )
                            } else {
                                SettingGroup(null) {
                                    hits.forEachIndexed { index, (hit, title, desc) ->
                                        ListRow(
                                            title, supporting = listOfNotNull(stringResource(hit.section.titleRes), desc).joinToString(" · "), icon = hit.icon,
                                            corners = groupCorners(index, hits.size),
                                            onClick = { if (hit.section == Section.About) onOpenAbout() else onSection(hit.section.name) },
                                            trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                        )
                                    }
                                }
                            }
                        } else Reveal(0) {
                            SettingGroup(null) {
                                rows.forEachIndexed { index, row ->
                                    val summary = when (row) {
                                        Section.Appearance -> stringResource(appearance.mode.labelRes) + " · " +
                                            (if (dynamic) stringResource(R.string.settings_dynamic_colour) else stringResource(appearance.seed.labelRes)) +
                                            " · " + stringResource(language.labelRes)
                                        Section.Spectrogram -> "$fftSize · ${windowFn.label}"
                                        Section.Library -> stringResource(R.string.settings_library_desc)
                                        Section.Glossary -> stringResource(R.string.glossary_desc)
                                        Section.Developer -> if (logging) stringResource(R.string.settings_logging) else null
                                        Section.About -> stringResource(R.string.about_tagline)
                                    }
                                    ListRow(
                                        stringResource(row.titleRes), supporting = summary, icon = row.icon,
                                        corners = groupCorners(index, rows.size),
                                        onClick = { if (row == Section.About) onOpenAbout() else onSection(row.name) },
                                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    )
                                }
                            }
                        }
                    }
                    Section.Appearance -> {
                        Reveal(0) { SettingGroup(stringResource(R.string.settings_preview)) { AppearancePreview(rowStyle, previewTracks, navLabels) } }
                        Reveal(0) {
                            // Dynamic colour needs Android 12, so older versions skip that row and always show the palette.
                            val dynamic = appearance.dynamicColor && DYNAMIC_COLOR_SUPPORTED
                            val paletteAt = if (DYNAMIC_COLOR_SUPPORTED) 2 else 1
                            val rows = 4 + (if (DYNAMIC_COLOR_SUPPORTED) 1 else 0) + (if (dynamic) 0 else 1)
                            SettingGroup(null) {
                                SettingBlock(stringResource(R.string.settings_theme), icon = Icons.Rounded.DarkMode, corners = groupCorners(0, rows)) {
                                    ChoiceTiles(
                                        ThemeMode.entries, appearance.mode, { if (it != appearance.mode) themed { onThemeMode(it) } }, { stringResource(it.labelRes) },
                                    ) { mode, tint ->
                                        Icon(
                                            when (mode) { ThemeMode.Light -> Icons.Rounded.LightMode; ThemeMode.Dark -> Icons.Rounded.DarkMode; ThemeMode.System -> Icons.Rounded.PhoneAndroid },
                                            contentDescription = null, tint = tint, modifier = Modifier.size(28.dp),
                                        )
                                    }
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
                                    SettingBlock(stringResource(R.string.settings_palette), supporting = stringResource(R.string.settings_palette_desc), icon = Icons.Rounded.ColorLens, corners = groupCorners(paletteAt, rows)) {
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
                                    corners = groupCorners(rows - 3, rows),
                                )
                                SettingToggleRow(
                                    title = stringResource(R.string.settings_reduce_motion),
                                    supporting = stringResource(R.string.settings_reduce_motion_desc),
                                    checked = appearance.reduceMotion,
                                    onChecked = onReduceMotion,
                                    icon = Icons.Rounded.Animation,
                                    corners = groupCorners(rows - 2, rows),
                                )
                                SettingBlock(stringResource(R.string.settings_app_language), supporting = stringResource(R.string.settings_language_desc), icon = Icons.Rounded.Language, corners = groupCorners(rows - 1, rows)) {
                                    OptionDropdown(
                                        AppLanguage.Choice.entries, language,
                                        { language = it; scope.launch { AppLanguage.change(context, it) } },
                                        { stringResource(it.labelRes) },
                                    )
                                }
                    }
                }

                        Reveal(1) {
                            SettingGroup(stringResource(R.string.settings_lists)) {
                                SettingBlock(stringResource(R.string.settings_density), supporting = stringResource(R.string.settings_density_desc), icon = Icons.Rounded.ViewAgenda, corners = groupCorners(0, 5)) {
                                    OptionSegmentedControl(
                                        RowDensity.entries, rowStyle.density, onDensity,
                                        { stringResource(if (it == RowDensity.Compact) R.string.density_compact else R.string.density_comfortable) },
                                    )
                                }
                                SettingBlock(stringResource(R.string.settings_cover_shape), supporting = stringResource(R.string.settings_cover_shape_desc), icon = Icons.Rounded.Image, corners = groupCorners(1, 5)) {
                                    ChoiceTiles(
                                        CoverShape.entries, rowStyle.coverShape, onCoverShape,
                                        { stringResource(when (it) { CoverShape.Rounded -> R.string.shape_rounded; CoverShape.Circle -> R.string.shape_circle; CoverShape.Cookie -> R.string.shape_cookie }) },
                                    ) { shape, tint ->
                                        androidx.compose.foundation.layout.Box(Modifier.size(36.dp).clip(shape.toShape()).background(tint.copy(alpha = 0.55f)))
                                    }
                                }
                                SettingToggleRow(
                                    title = stringResource(R.string.settings_show_result), supporting = stringResource(R.string.settings_show_result_desc),
                                    checked = rowStyle.showResult, onChecked = onShowResult, icon = Icons.Rounded.Verified, corners = groupCorners(2, 5),
                                )
                                SettingToggleRow(
                                    title = stringResource(R.string.settings_show_format), supporting = stringResource(R.string.settings_show_format_desc),
                                    checked = rowStyle.showFormat, onChecked = onShowFormat, icon = Icons.Rounded.AudioFile, corners = groupCorners(3, 5),
                                )
                                SettingToggleRow(
                                    title = stringResource(R.string.settings_show_folder), supporting = stringResource(R.string.settings_show_folder_desc),
                                    checked = rowStyle.showFolder, onChecked = onShowFolder, icon = Icons.Rounded.FolderOpen, corners = groupCorners(4, 5),
                                )
                            }
                        }

                    Reveal(2) {
                        SettingGroup(stringResource(R.string.settings_navigation)) {
                            SettingBlock(stringResource(R.string.settings_tab_titles), supporting = stringResource(R.string.settings_tab_titles_desc), icon = Icons.Rounded.Label, corners = groupCorners(0, 1)) {
                                OptionDropdown(
                                    com.rotatsulabs.specty.ui.design.components.NavLabels.entries, navLabels, onNavLabels,
                                    { stringResource(when (it) { com.rotatsulabs.specty.ui.design.components.NavLabels.Always -> R.string.tab_titles_always; com.rotatsulabs.specty.ui.design.components.NavLabels.ActiveOnly -> R.string.tab_titles_active; com.rotatsulabs.specty.ui.design.components.NavLabels.Never -> R.string.tab_titles_never }) },
                                )
                            }
                        }
                    }

                    }
                    Section.Library -> {
                        Reveal(0) { PrivacyCard() }
                        Reveal(1) {
                            SettingGroup(null) {
                                ListRow(
                                    stringResource(R.string.library_menu_folders), supporting = stringResource(R.string.settings_library_desc), icon = Icons.Rounded.FolderOpen,
                                    corners = groupCorners(0, 1), onClick = onOpenFolders,
                                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                )
                    }
                }

                Reveal(1) {
                    SettingGroup(stringResource(R.string.settings_scanning)) {
                        SettingToggleRow(
                            title = stringResource(R.string.settings_scan_start),
                            supporting = stringResource(R.string.settings_scan_start_desc),
                            checked = autoScan,
                            onChecked = onAutoScan,
                            icon = Icons.Rounded.Radar,
                            corners = groupCorners(0, 3),
                        )
                        SettingToggleRow(
                            title = stringResource(R.string.settings_keep_awake),
                            supporting = stringResource(R.string.settings_keep_awake_desc),
                            checked = keepAwake,
                            onChecked = onKeepAwake,
                            icon = Icons.Rounded.ScreenLockPortrait,
                            corners = groupCorners(1, 3),
                        )
                        SettingBlock(
                            stringResource(R.string.settings_skip_short),
                            supporting = stringResource(R.string.settings_skip_short_desc),
                            icon = Icons.Rounded.Timer,
                            corners = groupCorners(2, 3),
                        ) {
                            SegmentedControl(
                                listOf(
                                    Segment(0, stringResource(R.string.settings_skip_off)),
                                    Segment(30, stringResource(R.string.settings_skip_30)),
                                    Segment(60, stringResource(R.string.settings_skip_60)),
                                    Segment(120, stringResource(R.string.settings_skip_120)),
                                ),
                                minSeconds, onMinSeconds,
                            )
                        }
                    }
                }

                Reveal(1) {
                    SettingGroup(stringResource(R.string.settings_saved)) {
                        SettingBlock(
                            stringResource(R.string.settings_cache),
                            supporting = if (savedCount == 0) stringResource(R.string.settings_cache_empty) else pluralStringResource(R.plurals.settings_cache_count, savedCount, savedCount),
                            icon = Icons.Rounded.DeleteSweep,
                            corners = groupCorners(0, 1),
                        ) {
                            ExpressiveButton(stringResource(R.string.settings_clear_saved), onClearCache, kind = ButtonKind.Tonal, enabled = savedCount > 0)
                        }
                    }
                }

                    }
                    Section.Spectrogram -> {
                        val preset = SpecPreset.of(fftSize, windowFn)
                        var paletteIndex by remember {
                            mutableStateOf(
                                context.getSharedPreferences("specty", android.content.Context.MODE_PRIVATE)
                                    .getInt("spectrogram_palette", 0).coerceIn(0, SpectrogramPalette.entries.lastIndex),
                            )
                        }
                        val palette = SpectrogramPalette.entries[paletteIndex]
                        Reveal(0) { SpectrogramPreview(fftSize, windowFn, palette) }
                        Reveal(2) {
                            SettingGroup(null) {
                                SettingBlock(
                                    stringResource(R.string.settings_preset),
                                    supporting = stringResource(R.string.settings_preset_desc),
                                    icon = Icons.Rounded.Tune,
                                    corners = groupCorners(0, 4),
                                ) {
                                    OptionSegmentedControl(
                                        SpecPreset.entries, preset,
                                        { if (it != SpecPreset.Custom) { onFftSize(it.fft); onWindowFn(it.window) } },
                                        { stringResource(it.labelRes) },
                                    )
                                }
                                SettingBlock(
                                    stringResource(R.string.settings_window_size),
                                    supporting = stringResource(R.string.settings_window_size_desc),
                                    icon = Icons.Rounded.Straighten,
                                    corners = groupCorners(1, 4),
                                ) {
                                    OptionSegmentedControl(fftChoices, fftSize, onFftSize, { "$it" })
                                }
                                SettingBlock(stringResource(R.string.settings_window_fn), supporting = stringResource(windowFn.noteRes), icon = Icons.Rounded.Functions, corners = groupCorners(2, 4)) {
                                    OptionDropdown(WindowFn.entries.toList(), windowFn, onWindowFn, { it.label })
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.settings_window_apply),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                SettingBlock(
                                    stringResource(R.string.settings_spec_palette),
                                    supporting = stringResource(R.string.settings_spec_palette_desc),
                                    icon = Icons.Rounded.ColorLens,
                                    corners = groupCorners(3, 4),
                                ) {
                                    OptionDropdown(
                                        SpectrogramPalette.entries.toList(), palette,
                                        {
                                            paletteIndex = it.ordinal
                                            context.getSharedPreferences("specty", android.content.Context.MODE_PRIVATE).edit().putInt("spectrogram_palette", it.ordinal).apply()
                                        },
                                        { it.label },
                                    )
                                }
                            }
                        }
                    }
                    Section.Glossary -> {
                        val terms = listOf(
                            Triple(R.string.term_true_peak, R.string.term_true_peak_body, TermIcons.TruePeak),
                            Triple(R.string.term_dynamic_range, R.string.term_dynamic_range_body, TermIcons.DynamicRange),
                            Triple(R.string.term_lufs, R.string.term_lufs_body, TermIcons.Loudness),
                            Triple(R.string.term_cutoff, R.string.term_cutoff_body, TermIcons.Cutoff),
                            Triple(R.string.term_upsampled, R.string.term_upsampled_body, TermIcons.Upsampled),
                            Triple(R.string.term_band_limited, R.string.term_band_limited_body, TermIcons.BandLimited),
                            Triple(R.string.term_rolloff, R.string.term_rolloff_body, TermIcons.BandLimited),
                        )
                        Reveal(0) {
                            SettingGroup(null) {
                                terms.forEachIndexed { i, (name, body, icon) ->
                                    ListRow(stringResource(name), supporting = stringResource(body), icon = icon, corners = groupCorners(i, terms.size))
                                }
                            }
                        }
                    }
                    Section.Developer -> {
                        Reveal(0) {
                            SettingGroup(null) {
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
                    Section.About -> Unit
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * A miniature of the library in the live colours and list style: two track rows and the floating bar. A change of
 * theme, palette, pure black, row size, cover shape or row details shows here at once.
 */
@Composable
private fun AppearancePreview(style: RowStyle, tracks: List<Track>, navLabels: com.rotatsulabs.specty.ui.design.components.NavLabels) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        Modifier.fillMaxWidth(),
        shape = com.rotatsulabs.specty.ui.design.SpectyShapes.Card.toShape(),
        color = scheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Surface(
                Modifier.fillMaxWidth(),
                shape = com.rotatsulabs.specty.ui.design.SpectyShapes.Tile.toShape(),
                color = scheme.surface,
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    PreviewRow(style, Verdict.GENUINE, tracks.getOrNull(0))
                    PreviewRow(style, Verdict.POSSIBLE_LOSSY_SOURCE, tracks.getOrNull(1))
                }
            }
            PreviewNavBar(navLabels)
        }
    }
}

/** The real floating bar, so it can be held, swiped and tapped here exactly as in the library, with the chosen tab titles. */
@Composable
private fun PreviewNavBar(labels: com.rotatsulabs.specty.ui.design.components.NavLabels) {
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val items = com.rotatsulabs.specty.ui.spectyNavItems()
    com.rotatsulabs.specty.ui.design.components.SpectyNavigationBar(
        items, selected, { selected = it }, Modifier.fillMaxWidth(),
        separated = 1, labels = labels, embedded = true,
    )
}

@Composable
private fun PreviewRow(style: RowStyle, verdict: Verdict, track: Track?) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (style.compact) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        val coverSize = if (style.compact) 40.dp else 52.dp
        val placeholder: @Composable () -> Unit = {
            androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer) {
                androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
        }
        if (track != null) {
            CoverImage(uri = track.uri, shape = style.coverShape.toShape(), modifier = Modifier.size(coverSize), sizePx = 180, fallback = placeholder)
        } else {
            androidx.compose.foundation.layout.Box(Modifier.size(coverSize).clip(style.coverShape.toShape())) { placeholder() }
        }
        Column(Modifier.weight(1f)) {
            Text(track?.title?.withoutExplicitMark() ?: stringResource(R.string.settings_preview_title), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                (track?.artist ?: stringResource(R.string.settings_preview_sub)) + if (style.showFormat) "  ·  " + (track?.extension?.takeIf { it.isNotEmpty() } ?: "FLAC") else "",
                style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (style.showFolder) Text((track?.let { trackFolder(it) }) ?: "Music/Laufey", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        if (style.showResult) {
            com.rotatsulabs.specty.ui.design.components.VerdictLabel(verdict)
        }
    }
}

/** Big tiles to pick from, each with a small picture of its choice above the label, like the theme and shape pickers of other apps. */
@Composable
private fun <T> ChoiceTiles(items: List<T>, selected: T, onSelect: (T) -> Unit, label: @Composable (T) -> String, picture: @Composable (T, androidx.compose.ui.graphics.Color) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val on = item == selected
            val content = if (on) scheme.onSecondaryContainer else scheme.onSurfaceVariant
            androidx.compose.material3.Surface(
                onClick = { onSelect(item) },
                modifier = Modifier.weight(1f),
                shape = com.rotatsulabs.specty.ui.design.SpectyShapes.Tile.toShape(),
                color = if (on) scheme.secondaryContainer else scheme.surfaceContainerHigh,
                contentColor = content,
                border = if (on) androidx.compose.foundation.BorderStroke(2.dp, scheme.primary) else null,
            ) {
                Column(
                    Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.size(40.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { picture(item, content) }
                    Text(label(item), style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

/** Quick starting points for the window size and function; [Custom] stands for any other combination. */
private enum class SpecPreset(val labelRes: Int, val fft: Int, val window: WindowFn) {
    Fast(R.string.preset_fast, 1024, WindowFn.Hann),
    Balanced(R.string.preset_balanced, 2048, WindowFn.Hann),
    Detailed(R.string.preset_detailed, 4096, WindowFn.BlackmanHarris),
    Custom(R.string.preset_custom, 0, WindowFn.Hann);

    companion object {
        fun of(fft: Int, window: WindowFn): SpecPreset = entries.firstOrNull { it != Custom && it.fft == fft && it.window == window } ?: Custom
    }
}

/** A synthetic spectrogram drawn with the current window and palette, so a change shows at once. */
@Composable
private fun SpectrogramPreview(fftSize: Int, window: WindowFn, palette: SpectrogramPalette) {
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, fftSize, window, palette) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            SpectrogramRenderer.render(SpectrogramSample.build(fftSize, window), 0, palette).asImageBitmap()
        }
    }
    val description = stringResource(R.string.settings_spec_preview_desc)
    androidx.compose.material3.Surface(
        Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description },
        shape = com.rotatsulabs.specty.ui.design.SpectyShapes.Card.toShape(),
        color = androidx.compose.ui.graphics.Color.Black,
    ) {
        bitmap?.let {
            androidx.compose.foundation.Image(
                it, contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
            )
        }
    }
}

/** A plain statement of what the app can read and where data goes. */
@Composable
private fun PrivacyCard() {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Surface(
        Modifier.fillMaxWidth(),
        shape = com.rotatsulabs.specty.ui.design.SpectyShapes.Card.toShape(),
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_privacy_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The settings categories. The settings page lists them; each opens its own page, and About opens the About screen. */
private enum class Section(val titleRes: Int, val icon: ImageVector) {
    Appearance(R.string.settings_appearance, Icons.Rounded.Palette),
    Spectrogram(R.string.settings_spectrogram, Icons.Rounded.GraphicEq),
    Library(R.string.settings_library, Icons.Rounded.LibraryMusic),
    Glossary(R.string.glossary_title, Icons.AutoMirrored.Rounded.MenuBook),
    Developer(R.string.settings_developer, Icons.Rounded.Terminal),
    About(R.string.about_title, Icons.Rounded.Info),
}

/** One searchable setting: what the field matches against and the category a tap opens. */
private class SettingHit(val titleRes: Int, val descRes: Int?, val section: Section, val icon: ImageVector)

private val SETTING_HITS = listOf(
    SettingHit(R.string.settings_theme, null, Section.Appearance, Icons.Rounded.DarkMode),
    SettingHit(R.string.settings_dynamic_colour, R.string.settings_dynamic_colour_desc, Section.Appearance, Icons.Rounded.Palette),
    SettingHit(R.string.settings_palette, R.string.settings_palette_desc, Section.Appearance, Icons.Rounded.ColorLens),
    SettingHit(R.string.settings_pure_black, R.string.settings_pure_black_desc, Section.Appearance, Icons.Rounded.Contrast),
    SettingHit(R.string.settings_reduce_motion, R.string.settings_reduce_motion_desc, Section.Appearance, Icons.Rounded.Animation),
    SettingHit(R.string.settings_app_language, R.string.settings_language_desc, Section.Appearance, Icons.Rounded.Language),
    SettingHit(R.string.settings_density, R.string.settings_density_desc, Section.Appearance, Icons.Rounded.ViewAgenda),
    SettingHit(R.string.settings_cover_shape, R.string.settings_cover_shape_desc, Section.Appearance, Icons.Rounded.Image),
    SettingHit(R.string.settings_show_result, R.string.settings_show_result_desc, Section.Appearance, Icons.Rounded.Verified),
    SettingHit(R.string.settings_show_format, R.string.settings_show_format_desc, Section.Appearance, Icons.Rounded.AudioFile),
    SettingHit(R.string.settings_show_folder, R.string.settings_show_folder_desc, Section.Appearance, Icons.Rounded.FolderOpen),
    SettingHit(R.string.settings_tab_titles, R.string.settings_tab_titles_desc, Section.Appearance, Icons.Rounded.Label),
    SettingHit(R.string.settings_preset, R.string.settings_preset_desc, Section.Spectrogram, Icons.Rounded.GraphicEq),
    SettingHit(R.string.settings_window_size, R.string.settings_window_size_desc, Section.Spectrogram, Icons.Rounded.Straighten),
    SettingHit(R.string.settings_window_fn, null, Section.Spectrogram, Icons.Rounded.Functions),
    SettingHit(R.string.settings_spec_palette, R.string.settings_spec_palette_desc, Section.Spectrogram, Icons.Rounded.Palette),
    SettingHit(R.string.settings_scan_start, R.string.settings_scan_start_desc, Section.Library, Icons.Rounded.Radar),
    SettingHit(R.string.settings_keep_awake, R.string.settings_keep_awake_desc, Section.Library, Icons.Rounded.ScreenLockPortrait),
    SettingHit(R.string.settings_skip_short, R.string.settings_skip_short_desc, Section.Library, Icons.Rounded.Timer),
    SettingHit(R.string.settings_cache, null, Section.Library, Icons.Rounded.DeleteSweep),
    SettingHit(R.string.settings_logging, R.string.settings_logging_desc, Section.Developer, Icons.Rounded.Terminal),
    SettingHit(R.string.glossary_title, R.string.glossary_desc, Section.Glossary, Icons.AutoMirrored.Rounded.MenuBook),
    SettingHit(R.string.about_title, R.string.about_tagline, Section.About, Icons.Rounded.Info),
)

/** The pill above the settings list: typing replaces the categories with the settings that match. */
@Composable
private fun SettingsSearchField(query: String, onQuery: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = scheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text(stringResource(R.string.settings_search), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
            }
        }
        if (query.isNotEmpty()) {
            androidx.compose.material3.IconButton(onClick = { onQuery("") }) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.settings_search_clear), tint = scheme.onSurfaceVariant)
            }
        }
    }
}
