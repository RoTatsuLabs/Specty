package com.rotatsulabs.specty.batch

import com.rotatsulabs.specty.dev.AppLog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rotatsulabs.specty.MainActivity
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.AudioAnalyzer
import com.rotatsulabs.specty.audio.MediaProbe
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.data.ResultCache
import com.rotatsulabs.specty.data.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that analyses the queued library tracks one by one and stores each result as it goes, so a scan
 * that is stopped, cancelled or killed carries on from the same place the next time. The work is bounded: one file at
 * a time, a few CPU threads, a pause after each file, and a wait while the phone is hot or the battery is low.
 */
class BatchScanService : Service() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scanDispatcher = Dispatchers.Default.limitedParallelism(SCAN_THREADS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var lastNotify = 0L
    @Volatile private var timedOut = false
    /** Newest start command; stopSelf with it only stops the service when nothing newer is waiting. */
    @Volatile private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        BatchScanner.serviceAlive = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            if (job == null) stopSelf(startId)
            return START_NOT_STICKY
        }
        // Every startForegroundService call must be answered with startForeground, even when a scan is already running.
        createChannel()
        val total = BatchScanner.state.value.total
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            progressNotification("Preparing", 0, total, indeterminate = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (job?.isActive == true) return START_NOT_STICKY
        timedOut = false
        job = scope.launch { run() }
        return START_NOT_STICKY
    }

    /** Android 15 limits how long a data sync service may run. Stop in time; the next scan resumes where this one ended. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        timedOut = true
        job?.cancel()
    }

    private suspend fun run() {
        val cache = ResultCache.get(this)
        val analyzer = AudioAnalyzer(applicationContext, scanDispatcher, peakThreads = SCAN_THREADS)
        val fft = BatchScanner.fftSize
        val window = BatchScanner.window
        val queue = BatchScanner.queue
        var cancelled = false
        var note = ScanNote.None
        try {
            val engine = BatchEngine<Track>(
                isSaved = { it.sizeBytes > 0 && cache.contains(cache.keyOf(it.uri, it.sizeBytes, it.modifiedSec, fft, window)) },
                process = { track, onProgress ->
                    val result = analyzer.analyze(track.uri, fft, window) { _, p -> onProgress(p) }
                    if (track.sizeBytes > 0) {
                        val key = cache.keyOf(track.uri, track.sizeBytes, track.modifiedSec, fft, window)
                        withContext(Dispatchers.IO) { cache.save(key, result, publish = false) }
                    }
                    result.authenticity.verdict.let {
                        it == Verdict.LOSSY_SOURCE || it == Verdict.UPSAMPLED || it == Verdict.POSSIBLE_LOSSY_SOURCE
                    }
                },
                gate = { waitUntilSafe(queue.size) },
                cooldownMs = COOLDOWN_MS,
            )
            val stop = engine.run(queue, object : BatchListener<Track> {
                override fun onStart(index: Int, item: Track) {
                    BatchScanner.update { it.copy(current = item.title) }
                    notify(item.title, index, queue.size, force = true)
                }

                override fun onProgress(index: Int, fraction: Float) = notify(queue[index].title, index, queue.size, within = fraction)

                override fun onOutcome(index: Int, item: Track, outcome: BatchOutcome) {
                    BatchScanner.update {
                        when (outcome) {
                            BatchOutcome.Skipped -> it.copy(skipped = it.skipped + 1)
                            is BatchOutcome.Done -> it.copy(done = it.done + 1, flagged = it.flagged + if (outcome.flagged) 1 else 0)
                            is BatchOutcome.Failed -> { AppLog.w("Scan") { "Failed: ${item.title}" }; it.copy(failed = it.failed + 1) }
                        }
                    }
                }
            })
            note = stop.note
            AppLog.i("Scan") { "Stopped: ${stop.name}" }
        } catch (e: CancellationException) {
            cancelled = true
            AppLog.w("Scan") { "Cancelled${if (timedOut) " by the system time limit" else ""}" }
            if (timedOut) note = ScanNote.TimeLimit
        } finally {
            cache.publishNow()
            val s = BatchScanner.state.value
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, summaryNotification(s, cancelled, note))
            // The state flips last, so a new scan cannot be accepted while this service is still shutting down.
            BatchScanner.end(cancelled, note)
            stopSelf(lastStartId)
        }
    }

    /** Waits out a hot phone or a low battery; the scan carries on by itself once it is fine again. */
    private suspend fun waitUntilSafe(total: Int) {
        val power = getSystemService(PowerManager::class.java)
        var paused = false
        while (tooHot(power) || batteryLow()) {
            if (!paused) {
                paused = true
                BatchScanner.update { it.copy(note = ScanNote.Cooling) }
                AppLog.w("Scan") { "Paused: thermal or battery" }
                notify(getString(R.string.scan_notif_paused), BatchScanner.state.value.processed, total, force = true)
            }
            delay(PAUSE_POLL_MS)
        }
        if (paused) BatchScanner.update { it.copy(note = ScanNote.None) }
    }

    /** Thermal status exists from Android 10; older versions only wait on the battery. */
    private fun tooHot(power: PowerManager): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE

    private fun batteryLow(): Boolean {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val state = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL
        return !charging && level >= 0 && scale > 0 && level * 100 / scale < LOW_BATTERY_PERCENT
    }

    private fun notify(title: String, index: Int, total: Int, within: Float = 0f, force: Boolean = false) {
        val now = System.currentTimeMillis()
        // Forced updates (a new file) are still spaced out: a library of saved results would otherwise post thousands.
        if (now - lastNotify < if (force) 400 else 1000) return
        lastNotify = now
        val n = progressNotification(title, index, total, fraction = within)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(title: String, index: Int, total: Int, fraction: Float = 0f, indeterminate: Boolean = false): Notification {
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, BatchScanService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val max = 1000
        val progress = if (total == 0) 0 else ((index + fraction.coerceIn(0f, 1f)) / total * max).toInt()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(getString(R.string.scan_notif_title, (index + 1).coerceAtMost(total), total))
            .setContentText(title)
            .setProgress(max, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentIntent())
            .addAction(0, getString(R.string.action_cancel), cancel)
            .build()
    }

    private fun summaryNotification(s: BatchScanner.State, cancelled: Boolean, note: ScanNote): Notification {
        val summary = buildList {
            add(getString(R.string.scan_analysed, s.done))
            if (s.skipped > 0) add(getString(R.string.scan_skipped, s.skipped))
            if (s.flagged > 0) add(getString(R.string.scan_flagged, s.flagged))
            if (s.failed > 0) add(getString(R.string.scan_failed, s.failed))
        }.joinToString(getString(R.string.scan_sep))
        val text = if (note == ScanNote.None) summary else getString(R.string.scan_with_note, summary, getString(note.res, s.noteDetail))
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(
                getString(
                    when {
                        cancelled -> R.string.scan_notif_stopped
                        note != ScanNote.None -> R.string.scan_notif_stopped_early
                        else -> R.string.scan_notif_finished
                    },
                ),
            )
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.scan_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.scan_channel_desc)
                },
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        BatchScanner.serviceAlive = false
        // Never leave the screen saying a scan is running after the service is gone.
        if (BatchScanner.state.value.running) BatchScanner.end(cancelled = true, note = ScanNote.Interrupted)
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.rotatsulabs.specty.CANCEL_BATCH"
        private const val CHANNEL = "batch_scan"
        private const val NOTIFICATION_ID = 41
        /** CPU threads the scan uses at once; the rest are left to the phone. */
        private const val SCAN_THREADS = 2
        private const val COOLDOWN_MS = 150L
        private const val PAUSE_POLL_MS = 10_000L
        private const val LOW_BATTERY_PERCENT = 15
    }
}
