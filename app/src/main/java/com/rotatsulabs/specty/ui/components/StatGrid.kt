package com.rotatsulabs.specty.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

data class Stat(val icon: ImageVector, val label: String, val value: String)

@Composable
fun StatCell(stat: Stat, modifier: Modifier = Modifier) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(stat.icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = labelColor)
        Spacer(Modifier.width(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = labelColor)) { append(stat.label + ": ") }
                withStyle(SpanStyle(color = valueColor, fontWeight = FontWeight.Bold)) { append(stat.value) }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Two stats per row, label in the muted colour and value in bold. */
@Composable
fun StatGrid(stats: List<Stat>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        stats.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCell(pair[0], Modifier.weight(1f))
                if (pair.size > 1) StatCell(pair[1], Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
        }
    }
}
