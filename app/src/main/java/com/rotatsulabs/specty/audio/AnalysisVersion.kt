package com.rotatsulabs.specty.audio

/**
 * Versions of what a saved result contains and how it was measured.
 * Raise [SCHEMA] when the saved layout changes and [ALGORITHM] when a rule or threshold can change a verdict or a
 * measurement. Both are part of every cache key and of the cache header, so older results are dropped, not reused.
 */
object AnalysisVersion {
    const val SCHEMA = 7
    const val ALGORITHM = 3

    /** The authenticity verdict is always judged on this transform, whatever size the spectrogram is drawn at. */
    const val AUTH_FFT = 4096

    val tag: String get() = "s$SCHEMA.a$ALGORITHM"
}
