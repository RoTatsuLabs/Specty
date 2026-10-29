package com.rotatsulabs.specty.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

data class LibraryFolder(val uri: Uri, val name: String, val path: String)

/** The folders the person picked for the library, kept in preferences. Access to each is granted by the system picker. */
class LibraryFolders(private val context: Context, private val internalStorageLabel: String) {
    private val prefs = context.getSharedPreferences("specty", Context.MODE_PRIVATE)

    fun list(): List<LibraryFolder> =
        (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).map { describe(Uri.parse(it)) }.sortedBy { it.path.lowercase() }

    fun add(uri: Uri) = prefs.edit().putStringSet(KEY, uris() + uri.toString()).apply()

    fun remove(uri: Uri) = prefs.edit().putStringSet(KEY, uris() - uri.toString()).apply()

    /** Folders left out of the scan, including everything below them. */
    fun excluded(): List<LibraryFolder> =
        (prefs.getStringSet(KEY_OUT, emptySet()) ?: emptySet()).map { describe(Uri.parse(it)) }.sortedBy { it.path.lowercase() }

    /** Document ids of the excluded folders, as the scan compares them. */
    fun excludedIds(): List<String> =
        (prefs.getStringSet(KEY_OUT, emptySet()) ?: emptySet()).mapNotNull { runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(it)) }.getOrNull() }

    fun exclude(uri: Uri) = prefs.edit().putStringSet(KEY_OUT, outUris() + uri.toString()).apply()

    fun include(uri: Uri) = prefs.edit().putStringSet(KEY_OUT, outUris() - uri.toString()).apply()

    private fun outUris(): Set<String> = HashSet(prefs.getStringSet(KEY_OUT, emptySet()) ?: emptySet())

    private fun uris(): Set<String> = HashSet(prefs.getStringSet(KEY, emptySet()) ?: emptySet())

    private fun describe(uri: Uri): LibraryFolder {
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: uri.lastPathSegment ?: uri.toString()
        val label = AudioFiles.folderLabel(id, internalStorageLabel)
        return LibraryFolder(uri, label.name, label.path)
    }

    private companion object {
        const val KEY = "library_folders"
        const val KEY_OUT = "library_excluded"
    }
}
