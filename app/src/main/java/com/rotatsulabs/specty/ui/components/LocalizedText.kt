package com.rotatsulabs.specty.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.AuthenticityReport
import com.rotatsulabs.specty.audio.SpectrumShape

/** Localised counterpart of [Fmt.spectralEdge], which stays English for the exported image. */
@Composable
fun spectralEdge(report: AuthenticityReport): Pair<String, String> {
    val cutoff = report.cutoffHz
    val emptyAbove = report.emptyAboveHz
    return when {
        cutoff != null -> stringResource(R.string.edge_cutoff) to Fmt.kHz(cutoff)
        report.shape == SpectrumShape.GRADUAL_ROLLOFF && emptyAbove != null ->
            stringResource(R.string.edge_rolloff) to stringResource(R.string.edge_empty_above, Fmt.kHz(emptyAbove))
        report.shape == SpectrumShape.FULL_BAND -> stringResource(R.string.edge_cutoff) to stringResource(R.string.edge_none)
        else -> stringResource(R.string.edge_cutoff) to stringResource(R.string.edge_unknown)
    }
}
