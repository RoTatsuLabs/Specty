package com.rotatsulabs.specty.data

/** Decides which files in a picked folder are audio, and names a folder from its document id. */
object AudioFiles {
    private val extensions = setOf(
        "mp3", "m4a", "m4b", "aac", "flac", "wav", "ogg", "oga", "opus", "wma", "alac", "aif", "aiff", "ape", "wv",
        "mka", "ac3", "eac3", "ec3", "ac4", "dts", "caf", "dsf", "dff", "mpc", "tta",
    )

    /** Providers often report audio as `application/octet-stream`, so a known extension counts too. */
    fun isAudio(name: String, mime: String?): Boolean {
        if (mime != null && (mime.startsWith("audio/") || mime == "application/ogg")) return true
        return name.substringAfterLast('.', "").lowercase() in extensions
    }

    /** True when [documentId] is one of [excluded] or lies below one; ids compare by whole path parts, so `Music/A` does not hide `Music/AB`. */
    fun isExcluded(documentId: String, excluded: Collection<String>): Boolean = excluded.any { ex ->
        documentId == ex || documentId.startsWith(if (ex.endsWith('/') || ex.endsWith(':')) ex else "$ex/")
    }

    class FolderLabel(val name: String, val path: String)

    /** Label for a tree document id such as `primary:Music/Albums`; ids without a volume are shown as they are. */
    fun folderLabel(documentId: String, internalStorage: String = "Internal storage"): FolderLabel {
        if (':' !in documentId) return FolderLabel(documentId.trimEnd('/').substringAfterLast('/').ifEmpty { documentId }, documentId)
        val volume = documentId.substringBefore(':')
        val rest = documentId.substringAfter(':').trim('/')
        val volumeName = if (volume == "primary") internalStorage else volume
        val name = rest.substringAfterLast('/').ifEmpty { volumeName }
        val path = if (rest.isEmpty()) volumeName else "$volumeName/$rest"
        return FolderLabel(name, path)
    }
}
