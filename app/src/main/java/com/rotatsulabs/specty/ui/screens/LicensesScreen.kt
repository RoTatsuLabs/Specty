package com.rotatsulabs.specty.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import com.rotatsulabs.specty.ui.design.components.IconBadge
import com.rotatsulabs.specty.ui.design.components.ListRow
import com.rotatsulabs.specty.ui.design.components.SpectyCard
import com.rotatsulabs.specty.ui.design.components.groupCorners
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
        ExpressiveTopBar(title = "", onBack = onBack)
        when {
            shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ShapeLoader(size = 48.dp) }
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                item(key = "title") {
                    Text(
                        stringResource(R.string.licences_title), style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
                item(key = "hero") {
                    SpectyCard(Modifier.fillMaxWidth().padding(vertical = 8.dp), corners = SpectyShapes.Hero) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Description)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(stringResource(R.string.licences_title), style = MaterialTheme.typography.titleLarge)
                                Text(
                                    pluralStringResource(R.plurals.licences_count, loaded?.size ?: 0, loaded?.size ?: 0),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item(key = "search") {
                    SearchField(query, { query = it }, Modifier.padding(vertical = 8.dp), placeholder = stringResource(R.string.licences_search))
                }
                if (shown.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.licences_empty), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                itemsIndexed(shown, key = { _, e -> e.name + e.version }) { i, e ->
                    ListRow(
                        e.name,
                        supporting = (e.authors.take(1) + listOfNotNull(e.version)).joinToString("  ·  ").ifEmpty { null },
                        icon = Icons.Rounded.Code,
                        corners = groupCorners(i, shown.size),
                        onClick = { e.url?.let { runCatching { uriHandler.openUri(it) } } },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                e.licenses.firstOrNull()?.let { Chip(it) }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
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
            text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
