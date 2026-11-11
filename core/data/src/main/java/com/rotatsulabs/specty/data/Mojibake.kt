package com.rotatsulabs.specty.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Repairs tag text whose UTF-8 bytes were read as a single-byte encoding, so an apostrophe shows as `â€™`. The repair
 * only applies when the text is valid UTF-8 once turned back into bytes, which real accented text such as "Café" is not.
 */
object Mojibake {
    private val windows1252: Charset = Charset.forName("windows-1252")
    private val latin1: Charset = Charsets.ISO_8859_1

    fun fix(text: String?): String? {
        if (text.isNullOrEmpty() || text.all { it.code < 0x80 }) return text
        return repair(text, windows1252) ?: repair(text, latin1) ?: text
    }

    private fun repair(text: String, from: Charset): String? {
        val bytes = try {
            from.newEncoder().onUnmappableCharacter(CodingErrorAction.REPORT).onMalformedInput(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(text))
        } catch (_: CharacterCodingException) {
            return null
        }
        val decoded = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.array(), 0, bytes.limit())).toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        return decoded.takeIf { it != text }
    }
}
