package com.rotatsulabs.specty.audio

import android.net.Uri

data class AnalysisResult(
    val uri: Uri,
    val info: AudioInfo,
    val levels: LevelStats,
    val authenticity: AuthenticityReport,
    val spectrogram: Spectrogram,
    /** The PCM the measurements were taken from; null only when it is not known. */
    val decoded: DecodedFormat? = null,
)
