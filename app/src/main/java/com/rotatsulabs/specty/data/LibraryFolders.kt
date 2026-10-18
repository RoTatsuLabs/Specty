package com.rotatsulabs.specty.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class LibraryFolder(val uri: Uri, val name: String, val path: String)

/** The folders the person picked for the library, kept in preferences. Access to each is granted by the system picker. */
class LibraryFolders(context: Context) {
    private val prefs = context.getSharedPreferences("specty", Context.MODE_PRIVATE)

    fun list(): List<LibraryFolder> =
        (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).map { describe(Uri.parse(it)) }.sortedBy { it.path.lowercase() }

    fun add(uri: Uri) = prefs.edit().putStringSet(KEY, uris() + uri.toString()).apply()

    fun remove(uri: Uri) = prefs.edit().putStringSet(KEY, uris() - uri.toString()).apply()

    private fun uris(): Set<String> = HashSet(prefs.getStringSet(KEY, emptySet()) ?: emptySet())

    private fun describe(uri: Uri): LibraryFolder {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: uri.lastPathSegment ?: uri.toString()
        val label = AudioFiles.folderLabel(id)
        return LibraryFolder(uri, label.name, label.path)
    }

    private companion object {
        const val KEY = "library_folders"
    }
}
