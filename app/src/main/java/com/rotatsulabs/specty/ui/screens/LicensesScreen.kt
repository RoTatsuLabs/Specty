package com.rotatsulabs.specty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.ui.design.SpectyShapes
import com.rotatsulabs.specty.ui.design.components.ExpressiveTopBar
import com.rotatsulabs.specty.ui.design.components.MorphSurface
import com.rotatsulabs.specty.ui.design.components.SearchField
import com.rotatsulabs.specty.ui.design.components.ShapeLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What one row shows, kept apart from the library type so the filtering below can be tested without Android. */
class LicenseEntry(val name: String, val version: String?, val description: String?, val authors: List<String>, val licenses: List<String>, val url: String?)

internal fun Library.toEntry(): LicenseEntry = LicenseEntry(
    name = name,
    version = artifactVersion?.takeIf { it.isNotBlank() },
    description = description?.trim()?.takeIf { it.isNotEmpty() },
    authors = listOfNotNull(organization?.name?.takeIf { it.isNotBlank() })
        .ifEmpty { developers.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } } }
        .distinct(),
    licenses = licenses.map { it.spdxId ?: it.name }.distinct(),
    url = website?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: licenses.firstNotNullOfOrNull { it.url },
)

/** Keeps the entries whose name, author or licence contains [query], A to Z by name. */
internal fun filterLicenses(all: List<LicenseEntry>, query: String): List<LicenseEntry> {
    val q = query.trim().lowercase()
    val sorted = all.sortedBy { it.name.lowercase() }
    if (q.isEmpty()) return sorted
    return sorted.filter { e ->
        e.name.lowercase().contains(q) || e.authors.any { it.lowercase().contains(q) } || e.licenses.any { it.lowercase().contains(q) }
    }
}

/**
 * Every library Specty is built from, listed from the file the build generates, so a new dependency shows up here
 * without anyone editing a list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val loaded by produceState<List<LicenseEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val json = context.resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
                Libs.Builder().withJson(json).build().libraries.map { it.toEntry() }
            }.getOrDefault(emptyList())
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(loaded, query) { loaded?.let { filterLicenses(it, query) } }
    Column(modifier.fillMaxSize()) {
        ExpressiveTopBar(stringResource(R.string.licences_title), onBack = onBack)
        SearchField(
            query, { query = it },
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = stringResource(R.string.licences_search),
        )
        when {
            shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShapeLoader(size = 48.dp) }
            shown.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.licences_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(shown, key = { it.name + it.version }) { e ->
                    Column {
                        MorphSurface(
                            Modifier.fillMaxWidth(), corners = SpectyShapes.Row, color = androidx.compose.ui.graphics.Color.Transparent,
                            onClick = { e.url?.let { runCatching { uriHandler.openUri(it) } } },
                        ) {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(e.name, style = MaterialTheme.typography.titleLarge)
                                e.version?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                e.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    e.authors.forEach { Chip(it) }
                                    e.licenses.forEach { Chip(it) }
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                item(key = "bottom") { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Surface(shape = SpectyShapes.Pill.toShape(), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
