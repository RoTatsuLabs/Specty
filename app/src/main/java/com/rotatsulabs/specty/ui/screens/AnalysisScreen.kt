package com.rotatsulabs.specty.ui.screens

import com.rotatsulabs.specty.ui.components.withoutExplicitMark
import com.rotatsulabs.specty.ui.design.Corners
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ButtonKind
import com.rotatsulabs.specty.ui.design.components.ExpressiveButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.IconBadge
import com.rotatsulabs.specty.ui.design.components.MetricTile
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.Pill
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import com.rotatsulabs.specty.ui.design.components.SpectyCard
import com.rotatsulabs.specty.ui.design.components.WavyProgressBar
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.rotatsulabs.specty.ui.spectrogram.ReportExporter
import com.rotatsulabs.specty.ui.spectrogram.ReportFacts
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.HighQuality
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
import com.rotatsulabs.specty.audio.AudioInfo
import com.rotatsulabs.specty.audio.AuthenticityReport
import com.rotatsulabs.specty.audio.BitDepth
import com.rotatsulabs.specty.audio.DecodedFormat
import com.rotatsulabs.specty.audio.Codecs
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.ui.AnalysisState
import com.rotatsulabs.specty.ui.components.CoverImage
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.components.Reveal
import com.rotatsulabs.specty.ui.components.pressScale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import com.rotatsulabs.specty.ui.design.CoverFlight
import com.rotatsulabs.specty.ui.design.coverTarget
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
fun AnalysisScreen(state: AnalysisState, onBack: () -> Unit, onReanalyse: () -> Unit, modifier: Modifier = Modifier, showMetadata: Boolean = false, onFullscreen: (Int) -> Unit = {}, onCompare: () -> Unit = {}, flight: CoverFlight? = null) {
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
                        ExportMenu(state.result)
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
                is AnalysisState.Done -> ResultContent(state.result, showMetadata, onFullscreen, flight)
                is AnalysisState.Limited -> LimitedContent(state.info, state.report)
                AnalysisState.Idle -> Unit
            }
        }
    }
}

/** Top-bar action that saves or shares the whole result as one image. */
@Composable
private fun ExportMenu(result: AnalysisResult) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun export(share: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val uri = withContext(Dispatchers.Default) {
                val bmp = try { ReportExporter.render(context, result) } catch (_: OutOfMemoryError) { null }
                try {
                    bmp?.let {
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        SpectrogramExporter.savePng(context, it, ReportFacts.fileName(result.info.title, stamp))
                    }
                } finally {
                    bmp?.recycle()
                }
            }
            busy = false
            when {
                uri == null -> Toast.makeText(context, "Could not create the image", Toast.LENGTH_SHORT).show()
                share -> context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "Share analysis",
                    ),
                )
                else -> Toast.makeText(context, "Saved to Pictures/Specty", Toast.LENGTH_SHORT).show()
            }
        }
    }
    Box {
        ExpressiveIconButton(Icons.Rounded.IosShare, "Export as image", { open = true }, tonal = false)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(
                text = { Text(if (busy) "Working…" else "Save image to gallery") },
                leadingIcon = { Icon(Icons.Rounded.Download, null) },
                enabled = !busy,
                onClick = { open = false; export(false) },
            )
            DropdownMenuItem(
                text = { Text("Share image") },
                leadingIcon = { Icon(Icons.Rounded.IosShare, null) },
                enabled = !busy,
                onClick = { open = false; export(true) },
            )
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

/** A lossy file this device cannot decode: file facts and the container-based verdict, with the reason nothing else is shown. */
@Composable
private fun LimitedContent(info: AudioInfo, report: AuthenticityReport) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(info.title.withoutExplicitMark(), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        VerdictCard(report)
        FileCard(info, decoded = null, showDecoded = false)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ResultContent(result: AnalysisResult, showMetadata: Boolean, onFullscreen: (Int) -> Unit, flight: CoverFlight?) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Hero(result, flight)
        Reveal(1) { VerdictCard(result.authenticity) }
        Reveal(2) { MetricTiles(result) }
        Reveal(3) { ConfidenceCard(result.authenticity, result.info) }
        Reveal(4) { FileCard(result.info, result.decoded) }
        Reveal(5) { LevelsCard(result) }
        Reveal(6) { TimelineCard(result.levels) }
        Reveal(7) { ChannelsCard(result) }
        Reveal(8) { StereoCard(result.levels) }
        if (showMetadata) Reveal(9) { TagsCard(result.info.tags) }
        Reveal(10) { SpectrogramCard(
                result.spectrogram,
                title = result.info.title.withoutExplicitMark(),
                onFullscreen = onFullscreen,
                cutoffHz = result.authenticity.cutoffHz,
            ) }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(result: AnalysisResult, flight: CoverFlight?) {
    val info = result.info
    val verdict = result.authenticity.verdict
    val scheme = MaterialTheme.colorScheme
    // When the cover flies in from the library it must already sit at rest, so only the rest of the hero reveals.
    val shared = flight != null && flight.shares(result.uri)
    val cover: @Composable () -> Unit = {
        val coverSource = remember { MutableInteractionSource() }
        CoverImage(
            uri = result.uri,
            shape = SpectyShapes.Feature.toShape(),
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f)
                .coverTarget(flight, result.uri)
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
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (shared) cover() else Reveal(0) { cover() }
        Reveal(1) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    info.title.withoutExplicitMark(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Reveal(2) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val lossless = info.isLossless
                InfoChip(Codecs.displayName(info.codec), if (lossless) scheme.tertiaryContainer else scheme.secondaryContainer, if (lossless) scheme.onTertiaryContainer else scheme.onSecondaryContainer)
                InfoChip(Fmt.kHz(info.sampleRate.toDouble()))
                info.bitDepth?.let { InfoChip("$it-bit") }
                InfoChip(Fmt.size(info.sizeBytes))
                when {
                    info.isDolbyAtmos -> QualityBadge("DOLBY ATMOS", Icons.Rounded.SurroundSound, scheme.primary, scheme.tertiary)
                    verdict == Verdict.HI_RES -> QualityBadge("HI-RES LOSSLESS", Icons.Rounded.HighQuality, scheme.primary, scheme.secondary)
                    verdict == Verdict.GENUINE -> QualityBadge("LOSSLESS", Icons.Rounded.Verified, scheme.tertiary, scheme.secondary)
                }
                if (isExplicit(info.tags)) Pill("Explicit", icon = Icons.Rounded.Explicit)
            }
        }
    }
}

/** Quality tag (Dolby Atmos, Lossless, Hi-Res Lossless): a gradient capsule that pops in, with a pulsing icon and a light sweep. */
@Composable
private fun QualityBadge(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, from: Color, to: Color) {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "quality")
    val sweep by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        1f, 1.22f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 300f), label = "atmosPop")
    Row(
        Modifier
            .graphicsLayer {
                scaleX = pop
                scaleY = pop
            }
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(from, to)))
            .drawWithContent {
                drawContent()
                val x = size.width * (sweep * 1.8f - 0.4f)
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                        startX = x - size.width * 0.25f,
                        endX = x + size.width * 0.25f,
                    ),
                )
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp).graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
            tint = scheme.onPrimary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = scheme.onPrimary,
        )
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
        Verdict.GENUINE -> Quad(scheme.tertiaryContainer, scheme.onTertiaryContainer, Icons.Rounded.Verified, "Lossless, no sign of a lossy origin")
        Verdict.HI_RES -> Quad(scheme.primaryContainer, scheme.onPrimaryContainer, Icons.Rounded.HighQuality, "Hi-Res lossless, no sign of a lossy origin")
        Verdict.LOSSY_SOURCE -> Quad(scheme.errorContainer, scheme.onErrorContainer, Icons.Rounded.Warning, "Likely transcoded from a lossy source")
        Verdict.POSSIBLE_LOSSY_SOURCE -> Quad(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Rounded.WarningAmber, "Possibly from a lossy source")
        Verdict.UPSAMPLED -> Quad(scheme.errorContainer, scheme.onErrorContainer, Icons.Rounded.Warning, "Likely upsampled")
        Verdict.BAND_LIMITED -> Quad(scheme.secondaryContainer, scheme.onSecondaryContainer, Icons.Rounded.WarningAmber, "Band-limited spectrum")
        Verdict.LOSSY_CODEC -> Quad(scheme.surfaceContainerHigh, scheme.onSurface, Icons.Rounded.Info, "Lossy format")
        Verdict.DOLBY_ATMOS -> Quad(scheme.inverseSurface, scheme.inverseOnSurface, Icons.Rounded.SurroundSound, "Dolby Atmos")
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
        val tiles = listOf(
            Triple("Dynamic Range", Fmt.db(levels.dynamicRangeDb), SpectyShapes.TileStart),
            Triple("Loudness", Fmt.lufs(levels.lufs), SpectyShapes.TileMiddle),
            Triple("True Peak", Fmt.dbtp(levels.truePeakDbtp), SpectyShapes.TileEnd),
        )
        val colours = listOf(
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
        )
        tiles.forEachIndexed { i, (label, text, corners) ->
            MetricTile(
                label, text.substringBeforeLast(' '), Modifier.weight(1f),
                unit = text.substringAfterLast(' ', "").ifEmpty { null }, corners = corners,
                container = colours[i].first, content = colours[i].second, countUp = true, onClick = {},
            )
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
private fun FileCard(info: AudioInfo, decoded: DecodedFormat?, showDecoded: Boolean = true) {
    SectionCard("File") {
        StatGrid(
            listOfNotNull(
                Stat(Icons.Rounded.Memory, "Codec", Codecs.displayName(info.codec)),
                Stat(Icons.Rounded.GraphicEq, "Sample Rate", Fmt.kHz(info.sampleRate.toDouble())),
                Stat(
                    Icons.Rounded.MusicNote, "Source Bit Depth",
                    info.bitDepth?.let { bits -> "$bits-bit" + (info.sampleFormat?.let { " ($it)" } ?: "") } ?: "n/a",
                ),
                if (showDecoded) {
                    Stat(Icons.Rounded.DataObject, "Decoded PCM", (decoded ?: DecodedFormat(info.sampleRate, info.channels)).summary)
                } else {
                    null
                },
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
        val (edgeLabel, edgeValue) = Fmt.spectralEdge(result.authenticity)
        add(Stat(Icons.Rounded.FilterAlt, edgeLabel, edgeValue))
        add(Stat(Icons.Rounded.Tag, "Samples", Fmt.count(levels.framesPerChannel)))
        val effective = levels.effectiveBitDepth
        if (info.isLossless && effective != null) {
            add(Stat(Icons.Rounded.MusicNote, "Effective Depth", BitDepth.label(info.bitDepth, effective, levels.bitDepthBasis)))
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
