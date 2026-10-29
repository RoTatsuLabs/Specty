package com.rotatsulabs.specty.audio

/** What stopped an analysis, in terms the UI can word in the app language. */
enum class FailKey {
    STORAGE_FULL, FILE_TOO_LONG, NOT_ENOUGH_STORAGE, MORE_AUDIO_THAN_EXPECTED, NO_AUDIO,
    CANNOT_OPEN, NOT_AUDIO, NO_AUDIO_STREAM, NO_USABLE_FORMAT, DECODE_FAILED,
}

/** A failure as data: a key and its values (sizes in MB, the decoder's own detail text). */
data class Failure(val key: FailKey, val args: List<String>) {
    constructor(key: FailKey, vararg values: Any) : this(key, values.map { it.toString() })
}

/** Thrown for problems the person can act on. The message is the English text, for logs and as a fallback. */
class AnalysisFailure(val failure: Failure) : java.io.IOException(Failures.english(failure))

object Failures {
    fun english(f: Failure): String {
        val a = f.args
        return when (f.key) {
            FailKey.STORAGE_FULL -> "Storage is almost full. Free some space and try again."
            FailKey.FILE_TOO_LONG -> "This file is too long to analyse (about ${a[0]} MB of audio)."
            FailKey.NOT_ENOUGH_STORAGE -> "Not enough free storage to decode this file (about ${a[0]} MB needed)."
            FailKey.MORE_AUDIO_THAN_EXPECTED -> "This file holds more audio than expected, or storage is too low to decode it."
            FailKey.NO_AUDIO -> "The decoder produced no audio."
            FailKey.CANNOT_OPEN -> "Cannot open this file."
            FailKey.NOT_AUDIO -> "This file could not be read as audio."
            FailKey.NO_AUDIO_STREAM -> "No audio stream found in this file."
            FailKey.NO_USABLE_FORMAT -> "The audio stream has no usable sample rate or channel layout."
            FailKey.DECODE_FAILED -> "Decoding failed. ${a[0]}".trim()
        }
    }
}

/** Where an analysis is, for the progress text. */
enum class AnalysisStage { READING, DECODING, ANALYSING }
