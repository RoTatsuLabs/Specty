package com.rotatsulabs.specty.batch

import androidx.annotation.StringRes
import com.rotatsulabs.specty.R

/** Text for a scan note. Only [ScanNote.StartFailed] uses its argument, the system's own message. */
val ScanNote.res: Int
    @StringRes get() = when (this) {
        ScanNote.None -> error("ScanNote.None has no text")
        ScanNote.TooManyFailures -> R.string.scan_note_failures
        ScanNote.LowMemory -> R.string.scan_note_memory
        ScanNote.TimeLimit -> R.string.scan_note_time_limit
        ScanNote.Cooling -> R.string.scan_note_cooling
        ScanNote.Interrupted -> R.string.scan_note_interrupted
        ScanNote.StartFailed -> R.string.scan_note_start_failed
    }
