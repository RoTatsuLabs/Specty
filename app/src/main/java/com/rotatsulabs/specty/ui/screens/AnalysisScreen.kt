package com.rotatsulabs.specty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.rotatsulabs.specty.ui.components.Fmt
import com.rotatsulabs.specty.ui.components.Stat
import com.rotatsulabs.specty.ui.components.StatGrid
import com.rotatsulabs.specty.ui.spectrogram.SpectrogramCard

@Composable
fun AnalysisScreen(state: AnalysisState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val title = when (state) {
        is AnalysisState.Running -> state.title
        is AnalysisState.Done -> state.result.info.displayName
        is AnalysisState.Failed -> state.title
        AnalysisState.Idle -> ""
    }
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is AnalysisState.Running -> RunningContent(state)
                is AnalysisState.Failed -> FailedContent(state.message, onBack)
                is AnalysisState.Done -> ResultContent(state.result)
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
        CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 8.dp, strokeCap = androidx.compose.ui.graphics.StrokeCap.Round)
        Spacer(Modifier.height(24.dp))
        Text(state.stage, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FailedContent(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text("Could not analyse this file", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}

@Composable
private fun ResultContent(result: AnalysisResult) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VerdictCard(result.authenticity)
        AnalysisCard(result)
        SpectrogramCard(result.spectrogram)
        Spacer(Modifier.height(8.dp))
    }
}

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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(report.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private data class Quad(val container: Color, val content: Color, val icon: ImageVector, val headline: String)

@Composable
private fun AnalysisCard(result: AnalysisResult) {
    val info = result.info
    val levels = result.levels

    val fileStats = listOf(
        Stat(Icons.Rounded.Memory, "Codec", Codecs.displayName(info.codec)),
        Stat(Icons.Rounded.GraphicEq, "Sample Rate", Fmt.kHz(info.sampleRate.toDouble())),
        Stat(Icons.Rounded.MusicNote, "Bit Depth", info.bitDepth?.let { "$it-bit" } ?: "n/a"),
        Stat(Icons.Rounded.DataObject, "Decoded Format", info.sampleFormat ?: "—"),
        Stat(Icons.Rounded.Speed, "Bitrate", Fmt.bitrate(info.bitrateBps)),
        Stat(Icons.Rounded.Speaker, "Channels", info.channelLabel),
        Stat(Icons.Rounded.Timer, "Duration", Fmt.duration(info.durationSec)),
        Stat(Icons.Rounded.Waves, "Nyquist", Fmt.kHz(info.nyquistHz)),
        Stat(Icons.Rounded.Storage, "Size", Fmt.size(info.sizeBytes)),
    )

    val levelStats = buildList {
        add(Stat(Icons.Rounded.ShowChart, "Dynamic Range", Fmt.db(levels.dynamicRangeDb)))
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Audio Quality Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatGrid(fileStats)
            HorizontalDivider()
            StatGrid(levelStats)
            HorizontalDivider()
            Text("Per-channel Stats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                levels.channels.forEachIndexed { index, channel ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speaker, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append("Ch ${index + 1}: ") }
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(Fmt.perChannel(channel.peakDb, channel.rmsDb, channel.dynamicRangeDb))
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}
