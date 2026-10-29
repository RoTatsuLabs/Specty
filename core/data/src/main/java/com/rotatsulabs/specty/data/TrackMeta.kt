package com.rotatsulabs.specty.data

/** Tags and length read from a file once, kept so the next scan of the same file is instant. */
data class TrackMeta(val title: String?, val artist: String?, val album: String?, val durationMs: Long)

object TrackMetaCodec {
    fun keyOf(uri: String, sizeBytes: Long, modifiedSec: Long): String = "$uri|$sizeBytes|$modifiedSec"

    fun encode(entries: Map<String, TrackMeta>): String = buildString {
        for ((key, m) in entries) {
            append(clean(key)).append('\t')
                .append(clean(m.title)).append('\t')
                .append(clean(m.artist)).append('\t')
                .append(clean(m.album)).append('\t')
                .append(m.durationMs).append('\n')
        }
    }

    /** Lines that cannot be read are skipped; the result is only a cache. */
    fun decode(text: String): Map<String, TrackMeta> {
        val out = HashMap<String, TrackMeta>()
        for (line in text.lineSequence()) {
            val p = line.split('\t')
            if (p.size != 5 || p[0].isEmpty()) continue
            val duration = p[4].toLongOrNull() ?: continue
            out[p[0]] = TrackMeta(p[1].ifEmpty { null }, p[2].ifEmpty { null }, p[3].ifEmpty { null }, duration)
        }
        return out
    }

    private fun clean(s: String?): String = s?.replace('\t', ' ')?.replace('\n', ' ')?.replace('\r', ' ') ?: ""
}
