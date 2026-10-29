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

/** The file was read but its audio cannot be decoded here; [report] is what the container alone shows. */
class LimitedAnalysisException(val info: AudioInfo, val report: AuthenticityReport) :
    java.io.IOException("${Codecs.displayName(info.codec)} audio cannot be decoded on this device, so it could not be measured.")
