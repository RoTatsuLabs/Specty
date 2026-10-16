package com.rotatsulabs.specty.batch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.rotatsulabs.specty.MainActivity
import com.rotatsulabs.specty.R
import com.rotatsulabs.specty.audio.AudioAnalyzer
import com.rotatsulabs.specty.audio.MediaProbe
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.data.ResultCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Foreground service that analyses the queued library tracks one by one and stores each result. */
class BatchScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        createChannel()
        val total = BatchScanner.state.value.total
        startForeground(
            NOTIFICATION_ID,
            progressNotification("Preparing", 0, total, indeterminate = true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        job = scope.launch { run(this) }
        return START_NOT_STICKY
    }

    private suspend fun run(coroutines: CoroutineScope) {
        val cache = ResultCache.get(this)
        val analyzer = AudioAnalyzer(applicationContext)
        val fft = BatchScanner.fftSize
        val window = BatchScanner.window
        val queue = BatchScanner.queue
        var cancelled = false
        try {
            for ((index, track) in queue.withIndex()) {
                coroutines.ensureActive()
                BatchScanner.update { it.copy(current = track.title) }
                notify(track.title, index, queue.size, force = true)
                val key = cache.keyOf(track.uri, track.sizeBytes, track.modifiedSec, fft, window)
                if (track.sizeBytes > 0 && cache.contains(key)) {
                    BatchScanner.update { it.copy(skipped = it.skipped + 1) }
                    continue
                }
                try {
                    val result = analyzer.analyze(track.uri, fft, window) { _, p ->
                        notify(track.title, index, queue.size, within = p)
                    }
                    if (track.sizeBytes > 0) withContext(Dispatchers.IO) { cache.save(key, result) }
                    val flagged = result.authenticity.verdict.let {
                        it == Verdict.LOSSY_SOURCE || it == Verdict.UPSAMPLED || it == Verdict.POSSIBLE_LOSSY_SOURCE
                    }
                    BatchScanner.update { it.copy(done = it.done + 1, flagged = it.flagged + if (flagged) 1 else 0) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    BatchScanner.update { it.copy(failed = it.failed + 1) }
                }
            }
        } catch (e: CancellationException) {
            cancelled = true
        } finally {
            val s = BatchScanner.state.value
            BatchScanner.end(cancelled)
            stopForeground(STOP_FOREGROUND_REMOVE)
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID + 1, summaryNotification(s, cancelled))
            stopSelf()
        }
    }

    private fun notify(title: String, index: Int, total: Int, within: Float = 0f, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotify < 1000) return
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
            .setContentTitle("Scanning library  ${(index + 1).coerceAtMost(total)} of $total")
            .setContentText(title)
            .setProgress(max, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentIntent())
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun summaryNotification(s: BatchScanner.State, cancelled: Boolean): Notification {
        val text = buildString {
            append("${s.done} analysed")
            if (s.skipped > 0) append(", ${s.skipped} already saved")
            if (s.flagged > 0) append(", ${s.flagged} need a look")
            if (s.failed > 0) append(", ${s.failed} failed")
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(if (cancelled) "Library scan cancelled" else "Library scan finished")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Library scan", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Progress while Specty analyses your library in the background"
                },
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.rotatsulabs.specty.CANCEL_BATCH"
        private const val CHANNEL = "batch_scan"
        private const val NOTIFICATION_ID = 41
    }
}
