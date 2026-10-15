package com.rotatsulabs.specty.ui.screens

import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.IconBadge
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.Pill
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import com.rotatsulabs.specty.ui.design.components.SpectyCard
import com.rotatsulabs.specty.ui.design.components.WavyProgressBar
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rotatsulabs.specty.audio.AnalysisResult
import com.rotatsulabs.specty.audio.AuthenticityReport
import com.rotatsulabs.specty.audio.Codecs
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.AnalysisState
import com.rotatsulabs.specty.ui.components.CoverImage
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.components.Reveal
import com.rotatsulabs.specty.ui.components.pressScale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.scale
import java.util.Locale
import kotlin.math.roundToInt
import com.rotatsulabs.specty.ui.components.Stat
import com.rotatsulabs.specty.ui.components.StatGrid
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramCard
import com.rotatsulabs.specty.ui.theme.Motion
import kotlinx.coroutines.delay

@Composable
fun AnalysisScreen(state: AnalysisState, onBack: () -> Unit, onReanalyse: () -> Unit, modifier: Modifier = Modifier, showMetadata: Boolean = false, onFullscreen: (Int) -> Unit = {}, onCompare: () -> Unit = {}) {
    val title = when (state) {
        is AnalysisState.Running -> state.title
        is AnalysisState.Failed -> state.title
        else -> ""
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ExpressiveTopBar(
                title = title,
                onBack = onBack,
                modifier = Modifier,
                actions = {
                    if (state is AnalysisState.Done) {
                        ExpressiveIconButton(Icons.Rounded.Compare, "Compare with another file", onCompare, tonal = false)
                        var spins by remember { mutableStateOf(0) }
                        val spin by animateFloatAsState(spins * 360f, spring(dampingRatio = 0.6f, stiffness = 120f), label = "spin")
                        ExpressiveIconButton(
                            Icons.Rounded.Refresh, "Analyse again", { spins++; onReanalyse() },
                            tonal = false, iconModifier = Modifier.graphicsLayer { rotationZ = spin },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is AnalysisState.Running -> RunningContent(state)
                is AnalysisState.Failed -> FailedContent(state.message, onBack)
                is AnalysisState.Done -> ResultContent(state.result, state.cached, showMetadata, onFullscreen)
                AnalysisState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun RunningContent(state: AnalysisState.Running) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShapeLoader(size = 80.dp)
        Spacer(Modifier.height(28.dp))
        Text(state.stage, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        WavyProgressBar(state.progress)
    }
}

@Composable
private fun FailedContent(message: String, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        SpectyCard(corners = SpectyShapes.Hero, color = scheme.errorContainer) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = scheme.onErrorContainer)
            Spacer(Modifier.height(8.dp))
            Text("Could not analyse this file", style = MaterialTheme.typography.titleMedium, color = scheme.onErrorContainer)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onErrorContainer)
            Spacer(Modifier.height(12.dp))
            ExpressiveButton("Back", onBack, kind = ButtonKind.Tonal)
        }
    }
}

@Composable
private fun ResultContent(result: AnalysisResult, cached: Boolean, showMetadata: Boolean, onFullscreen: (Int) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Reveal(0) { Hero(result, cached) }
        Reveal(1) { VerdictCard(result.authenticity) }
        Reveal(2) { MetricTiles(result) }
        Reveal(3) { ConfidenceCard(result.authenticity, result.info) }
        Reveal(4) { FileCard(result) }
        Reveal(5) { LevelsCard(result) }
        Reveal(6) { TimelineCard(result.levels) }
        Reveal(7) { ChannelsCard(result) }
        Reveal(8) { StereoCard(result.levels) }
        if (showMetadata) Reveal(9) { TagsCard(result.info.tags) }
        Reveal(10) { SpectrogramCard(
                result.spectrogram,
                title = result.info.displayName,
                onFullscreen = onFullscreen,
                cutoffHz = result.authenticity.cutoffHz.takeIf { result.authenticity.hasCliff },
            ) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Hero(result: AnalysisResult, cached: Boolean) {
    val info = result.info
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val coverSource = remember { MutableInteractionSource() }
        CoverImage(
            uri = result.uri,
            shape = SpectyShapes.Feature.toShape(),
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .pressScale(coverSource, 0.94f)
                .clickable(interactionSource = coverSource, indication = null) { },
            sizePx = 768,
        ) {
            Surface(Modifier.fillMaxSize(), color = scheme.tertiaryContainer, contentColor = scheme.onTertiaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(72.dp))
                }
            }
        }
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                info.displayName.substringBeforeLast('.'),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isExplicit(info.tags)) {
                Icon(Icons.Rounded.Explicit, contentDescription = "Explicit", tint = scheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            val lossless = info.isLossless
            InfoChip(Codecs.displayName(info.codec), if (lossless) scheme.tertiaryContainer else scheme.secondaryContainer, if (lossless) scheme.onTertiaryContainer else scheme.onSecondaryContainer)
            InfoChip(Fmt.kHz(info.sampleRate.toDouble()))
            info.bitDepth?.let { InfoChip("$it-bit") }
            InfoChip(Fmt.size(info.sizeBytes))
            if (cached) InfoChip("Saved result", scheme.primaryContainer, scheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurface,
) = Pill(text, container = container, content = content)

@Composable
private fun VerdictCard(report: AuthenticityReport) {
    val scheme = MaterialTheme.colorScheme
    val (container, content, icon, headline) = when (report.verdict) {
        Verdict.GENUINE -> Quad(scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Rounded.Verified, "Looks like genuine full-band audio")
        Verdict.LOSSY_SOURCE -> Quad(scheme.errorContainer, scheme.onErrorContainer, Icons.Rounded.Warning, "Likely transcoded from a lossy source")
        Verdict.POSSIBLE_LOSSY_SOURCE -> Quad(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Rounded.WarningAmber, "Possibly from a lossy source")
        Verdict.UPSAMPLED -> Quad(scheme.errorContainer, scheme.onErrorContainer, Icons.Rounded.Warning, "Likely upsampled")
        Verdict.BAND_LIMITED -> Quad(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Rounded.WarningAmber, "Band-limited spectrum")
        Verdict.LOSSY_CODEC -> Quad(scheme.surfaceContainerHigh, scheme.onSurface, Icons.Rounded.Info, "Lossy format")
        Verdict.INCONCLUSIVE -> Quad(scheme.surfaceContainerHigh, scheme.onSurface, Icons.Rounded.Info, "Inconclusive")
    }
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(350)
        popped = true
    }
    val pop by animateFloatAsState(if (popped) 1f else 0f, spring(dampingRatio = 0.4f, stiffness = 260f), label = "verdictPop")
    SpectyCard(modifier = Modifier.fillMaxWidth(), corners = SpectyShapes.Hero, color = container) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                IconBadge(icon, Modifier.scale(pop), container = content.copy(alpha = 0.14f), content = content, corners = Corners(24.dp), size = 52.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(headline, style = MaterialTheme.typography.titleMedium)
                    Text(report.note, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private data class Quad(val container: Color, val content: Color, val icon: ImageVector, val headline: String)

/** Three headline numbers as a joined row of tiles, with large radii outside and small ones between. */
@Composable
private fun MetricTiles(result: AnalysisResult) {
    val levels = result.levels
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        MetricTile("Dynamic Range", Fmt.db(levels.dynamicRangeDb), SpectyShapes.TileStart, scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.weight(1f))
        MetricTile("Loudness", Fmt.lufs(levels.lufs), SpectyShapes.TileMiddle, scheme.secondaryContainer, scheme.onSecondaryContainer, Modifier.weight(1f))
        MetricTile("True Peak", Fmt.dbtp(levels.truePeakDbtp), SpectyShapes.TileEnd, scheme.tertiaryContainer, scheme.onTertiaryContainer, Modifier.weight(1f))
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    corners: Corners,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    val number = value.substringBeforeLast(' ')
    val unit = if (' ' in value) value.substringAfterLast(' ') else ""
    val target = number.toDoubleOrNull()
    val decimals = number.substringAfter('.', "").length
    val progress = remember { Animatable(0f) }
    LaunchedEffect(number) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1100, delayMillis = 250, easing = FastOutSlowInEasing))
    }
    val shown = if (target != null) String.format(Locale.US, "%.${decimals}f", target * progress.value) else number
    MorphSurface(modifier, corners, container, content, onClick = {}) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(shown, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            Text(unit.ifEmpty { " " }, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    SpectyCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun FileCard(result: AnalysisResult) {
    val info = result.info
    SectionCard("File") {
        StatGrid(
            listOf(
                Stat(Icons.Rounded.Memory, "Codec", Codecs.displayName(info.codec)),
                Stat(Icons.Rounded.GraphicEq, "Sample Rate", Fmt.kHz(info.sampleRate.toDouble())),
                Stat(Icons.Rounded.MusicNote, "Bit Depth", info.bitDepth?.let { "$it-bit" } ?: "n/a"),
                Stat(Icons.Rounded.DataObject, "Decoded Format", info.sampleFormat ?: "—"),
                Stat(Icons.Rounded.Speed, "Bitrate", Fmt.bitrate(info.bitrateBps)),
                Stat(Icons.Rounded.Speaker, "Channels", info.channelLabel),
                Stat(Icons.Rounded.Timer, "Duration", Fmt.duration(info.durationSec)),
                Stat(Icons.Rounded.Waves, "Nyquist", Fmt.kHz(info.nyquistHz)),
                Stat(Icons.Rounded.Storage, "Size", Fmt.size(info.sizeBytes)),
            ),
        )
    }
}

@Composable
private fun LevelsCard(result: AnalysisResult) {
    val info = result.info
    val levels = result.levels
    val stats = buildList {
        add(Stat(Icons.Rounded.ShowChart, "Dynamic Range", Fmt.db(levels.dynamicRangeDb)))
        levels.drTt?.let { add(Stat(Icons.Rounded.ShowChart, "DR meter", "DR${it.roundToInt()}")) }
        add(Stat(Icons.Rounded.Equalizer, "Peak", Fmt.db(levels.peakDb)))
        add(Stat(Icons.Rounded.BarChart, "RMS", Fmt.db(levels.rmsDb)))
        add(Stat(Icons.AutoMirrored.Rounded.VolumeUp, "LUFS", Fmt.lufs(levels.lufs)))
        add(Stat(Icons.Rounded.WarningAmber, "True Peak", Fmt.dbtp(levels.truePeakDbtp)))
        add(
            Stat(
                Icons.Rounded.ErrorOutline,
                "Clipping",
                if (levels.clippedSamples == 0L) "No clipping" else "${Fmt.count(levels.clippedSamples)} samples",
            ),
        )
        add(Stat(Icons.Rounded.FilterAlt, "Spectral Cutoff", Fmt.kHz(result.authenticity.cutoffHz)))
        add(Stat(Icons.Rounded.Tag, "Samples", Fmt.count(levels.framesPerChannel)))
        val declared = info.bitDepth
        val effective = levels.effectiveBitDepth
        if (info.isLossless && declared != null && declared >= 24 && effective != null) {
            add(Stat(Icons.Rounded.MusicNote, "Effective Depth", "$effective-bit"))
        }
    }
    SectionCard("Levels and dynamics") { StatGrid(stats) }
}

@Composable
private fun ChannelsCard(result: AnalysisResult) {
    SectionCard("Per-channel stats") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            result.levels.channels.forEachIndexed { index, channel ->
                MorphSurface(Modifier.fillMaxWidth(), Corners(20.dp), MaterialTheme.colorScheme.surfaceContainerHigh, morphOnPress = false) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("Ch ${index + 1}")
                        Spacer(Modifier.width(14.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(Fmt.perChannel(channel.peakDb, channel.rmsDb, channel.dynamicRangeDb))
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
