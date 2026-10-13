package com.rotatsulabs.specty.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val fileName: String,
) {
    val extension: String get() = fileName.substringAfterLast('.', "").uppercase()
}

object LibraryRepository {
    /** Scans MediaStore for audio at least 10 seconds long. Needs READ_MEDIA_AUDIO. */
    suspend fun scan(context: Context): List<Track> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf("10000")
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val tracks = ArrayList<Track>()
        context.contentResolver.query(collection, projection, selection, args, order)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val fileCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (cursor.moveToNext()) {
                val fileName = cursor.getString(fileCol) ?: continue
                tracks += Track(
                    uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                    title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
                    artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" },
                    album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() },
                    durationMs = cursor.getLong(durationCol),
                    sizeBytes = cursor.getLong(sizeCol),
                    fileName = fileName,
                )
            }
        }
        tracks
    }
}
