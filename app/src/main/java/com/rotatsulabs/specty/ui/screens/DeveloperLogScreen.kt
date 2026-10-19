package com.rotatsulabs.specty.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rotatsulabs.specty.dev.AppLog
import com.rotatsulabs.specty.ui.design.components.ExpressiveIconButton
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.SpectyCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Diagnostics, the last crash and the log, with copy, share and clear. */
@Composable
fun DeveloperLogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var diagnostics by remember { mutableStateOf("") }
    var crash by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        withContext(Dispatchers.IO) {
            diagnostics = AppLog.diagnostics(context)
            crash = AppLog.lastCrash()
            lines = AppLog.read(60_000).lines().filter { it.isNotEmpty() }.takeLast(1_500).asReversed()
        }
    }

    fun report(): String = buildString {
        appendLine(diagnostics)
        crash?.let { appendLine(); appendLine("Last crash:"); appendLine(it) }
        appendLine(); appendLine("Log:")
        append(AppLog.read(100_000))
    }

    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(
            "Developer log", onBack = onBack,
            actions = {
                ExpressiveIconButton(Icons.Rounded.ContentCopy, "Copy report", {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Specty report", report()))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }, tonal = false)
                ExpressiveIconButton(Icons.Rounded.IosShare, "Share report", {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, report()) }
                    context.startActivity(Intent.createChooser(send, "Share report"))
                }, tonal = false)
                ExpressiveIconButton(Icons.Rounded.DeleteSweep, "Clear log", {
                    AppLog.clear(); AppLog.clearCrash(); reload++
                }, tonal = false)
            },
        )
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    SpectyCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(diagnostics, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
                crash?.let {
                    item {
                        SpectyCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text("Last crash", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Text(it.take(4_000), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                if (lines.isEmpty()) {
                    item { Text("The log is empty. Turn logging on in Settings and use the app.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(lines) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp) }
            }
        }
    }
}
