package com.rotatsulabs.specty.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import org.json.JSONObject
import java.io.IOException

/** Reads stream facts with FFprobe, working directly on content:// URIs through FFmpegKit's SAF bridge. */
object MediaProbe {
    data class FileMeta(val displayName: String, val sizeBytes: Long, val modified: Long = 0L)

    fun queryMeta(context: Context, uri: Uri): FileMeta {
        var name: String? = null
        var size = -1L
        runCatching {
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        return FileMeta(name ?: uri.lastPathSegment ?: "Audio file", size, queryModified(context, uri))
    }

    /** Last-modified time in seconds from MediaStore or a document provider, or 0 when neither knows it. */
    private fun queryModified(context: Context, uri: Uri): Long {
        for ((column, divisor) in listOf("date_modified" to 1L, "last_modified" to 1000L)) {
            val value = runCatching {
                context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) / divisor else null
                }
            }.getOrNull()
            if (value != null && value > 0) return value
        }
        return 0L
    }

    fun probe(context: Context, uri: Uri): AudioInfo {
        val meta = queryMeta(context, uri)
        val saf = FFmpegKitConfig.getSafParameterForRead(context, uri)
            ?: throw AnalysisFailure(Failure(FailKey.CANNOT_OPEN))
        val session = FFprobeKit.getMediaInformation(saf)
        val media = session.mediaInformation
            ?: throw AnalysisFailure(Failure(FailKey.NOT_AUDIO))
        val stream = media.streams?.firstOrNull { it.type == "audio" }
            ?: throw AnalysisFailure(Failure(FailKey.NO_AUDIO_STREAM))
        val props = stream.allProperties ?: JSONObject()

        val sampleRate = props.optString("sample_rate").toIntOrNull() ?: 0
        val channels = props.optInt("channels", 0)
        if (sampleRate <= 0 || channels <= 0) throw AnalysisFailure(Failure(FailKey.NO_USABLE_FORMAT))

        val rawBits = props.optString("bits_per_raw_sample").toIntOrNull()?.takeIf { it > 0 }
        val sampleBits = props.optInt("bits_per_sample", 0).takeIf { it > 0 }

        return AudioInfo(
            displayName = meta.displayName,
            codec = props.optString("codec_name").ifEmpty { "unknown" },
            container = media.format,
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = rawBits ?: sampleBits,
            sampleFormat = props.optString("sample_fmt").ifEmpty { null },
            bitrateBps = props.optString("bit_rate").toLongOrNull() ?: media.bitrate?.toLongOrNull(),
            durationSec = media.duration?.toDoubleOrNull()
                ?: props.optString("duration").toDoubleOrNull()
                ?: 0.0,
            sizeBytes = if (meta.sizeBytes > 0) meta.sizeBytes else media.size?.toLongOrNull() ?: 0L,
            tags = readTags(media.tags, stream.tags),
            profile = atmosProfile(context, uri, props.optString("codec_name"), media.format)
                ?: props.optString("profile").ifEmpty { null },
        )
    }

    private const val ATMOS_PROFILE = "Dolby Digital Plus + Dolby Atmos"

    /** E-AC-3 in MP4 carries Atmos as a flag FFprobe does not report, so read the `dec3` box directly. */
    private fun atmosProfile(context: Context, uri: Uri, codec: String, format: String?): String? {
        if (codec != "eac3" || format?.contains("mp4") != true) return null
        val found = runCatching {
            AtmosProbe.isAtmos { context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open") }
        }.getOrDefault(false)
        return if (found) ATMOS_PROFILE else null
    }

    /** Merges container and stream tags (Ogg and Opus keep theirs on the stream), dropping lyrics and duplicates. */
    private fun readTags(vararg sources: JSONObject?): List<Pair<String, String>> {
        val seen = HashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        for (json in sources) {
            if (json == null) continue
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val lower = key.lowercase()
                if ("lyric" in lower || lower == "uslt" || lower == "sylt") continue
                val value = json.optString(key).trim()
                if (value.isEmpty() || !seen.add(lower)) continue
                out += key to value.take(MAX_TAG_LENGTH)
            }
        }
        return out
    }

    private const val MAX_TAG_LENGTH = 4000
}
