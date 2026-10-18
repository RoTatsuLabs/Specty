package com.rotatsulabs.specty.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val fileName: String,
    val modifiedSec: Long = 0L,
) {
    val extension: String get() = fileName.substringAfterLast('.', "").uppercase()
}

/**
 * Lists the audio files inside the folders the person picked. Files appear at once with names taken from the file
 * name; tags and length are read afterwards (a few files at a time) and remembered, so the next scan is quick.
 */
object LibraryRepository {
    private const val MIN_DURATION_MS = 10_000L
    private const val EMIT_EVERY = 30
    private const val CACHE_FILE = "library_meta.tsv"

    private class Entry(val uri: Uri, val name: String, val size: Long, val modifiedSec: Long) {
        val key: String get() = TrackMetaCodec.keyOf(uri.toString(), size, modifiedSec)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val reader = Dispatchers.IO.limitedParallelism(4)

    /** [onUpdate] receives the list as it fills in; the final list is returned. */
    suspend fun scan(context: Context, folders: List<Uri>, onUpdate: (List<Track>) -> Unit): List<Track> = coroutineScope {
        val entries = withContext(Dispatchers.IO) {
            folders.flatMap { list(context, it) { ensureActive() } }.distinctBy { it.uri }
        }
        val known = withContext(Dispatchers.IO) { loadCache(context) }
        val meta = ConcurrentHashMap<String, TrackMeta>()
        for (e in entries) known[e.key]?.let { meta[e.key] = it }

        val lock = Any()
        fun snapshot(): List<Track> = entries.mapNotNull { e -> track(e, meta[e.key]) }
        fun emit() = synchronized(lock) { onUpdate(snapshot()) }

        emit()
        val missing = entries.filter { it.key !in meta }
        if (missing.isNotEmpty()) {
            val done = AtomicInteger()
            missing.map { e ->
                async(reader) {
                    ensureActive()
                    meta[e.key] = readMeta(context, e.uri)
                    if (done.incrementAndGet() % EMIT_EVERY == 0) emit()
                }
            }.awaitAll()
            withContext(Dispatchers.IO) { saveCache(context, entries.mapNotNull { e -> meta[e.key]?.let { e.key to it } }.toMap()) }
        }
        snapshot()
    }

    /** A file shorter than ten seconds is a sound effect, not music; one with no known length is kept. */
    private fun track(e: Entry, m: TrackMeta?): Track? {
        val duration = m?.durationMs ?: 0L
        if (duration in 1 until MIN_DURATION_MS) return null
        return Track(
            uri = e.uri,
            title = m?.title?.takeIf { it.isNotBlank() } ?: e.name.substringBeforeLast('.', e.name),
            artist = m?.artist?.takeIf { it.isNotBlank() && it != "<unknown>" },
            album = m?.album?.takeIf { it.isNotBlank() },
            durationMs = duration,
            sizeBytes = e.size,
            fileName = e.name,
            modifiedSec = e.modifiedSec,
        )
    }

    /** Walks [tree] and everything below it. A folder the provider cannot list is skipped. */
    private fun list(context: Context, tree: Uri, ensureActive: () -> Unit): List<Entry> {
        val out = ArrayList<Entry>()
        val seen = HashSet<String>()
        val pending = ArrayDeque<String>()
        val root = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return out
        pending.addLast(root)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        while (pending.isNotEmpty()) {
            ensureActive()
            val parent = pending.removeFirst()
            if (!seen.add(parent)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent)
            runCatching {
                context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            if (!name.startsWith(".")) pending.addLast(id)
                        } else if (AudioFiles.isAudio(name, mime)) {
                            out += Entry(
                                DocumentsContract.buildDocumentUriUsingTree(tree, id),
                                name,
                                if (c.isNull(4)) 0L else c.getLong(4),
                                if (c.isNull(3)) 0L else c.getLong(3) / 1000,
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    private fun readMeta(context: Context, uri: Uri): TrackMeta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            TrackMeta(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            )
        } catch (_: Exception) {
            TrackMeta(null, null, null, 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun loadCache(context: Context): Map<String, TrackMeta> =
        runCatching { TrackMetaCodec.decode(File(context.filesDir, CACHE_FILE).readText()) }.getOrDefault(emptyMap())

    private fun saveCache(context: Context, entries: Map<String, TrackMeta>) {
        runCatching {
            val target = File(context.filesDir, CACHE_FILE)
            val temp = File(context.filesDir, "$CACHE_FILE.tmp")
            temp.writeText(TrackMetaCodec.encode(entries))
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }
    }
}
