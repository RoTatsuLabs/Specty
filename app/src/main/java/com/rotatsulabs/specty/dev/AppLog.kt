package com.rotatsulabs.specty.dev

import android.content.Context
import android.os.Build
import com.rotatsulabs.specty.BuildConfig
import java.io.File
import java.util.concurrent.Executors

/**
 * Developer logging. Off until the user turns it on in Settings > Developer; messages are lazy, so a disabled log costs
 * one boolean check. The last crash is always kept (one small file) so it can be read after a restart.
 */
object AppLog {
    const val KEY_DEVELOPER = "developer_mode"
    const val KEY_LOGGING = "dev_logging"

    @Volatile var enabled = false
    private var store: LogStore? = null
    private var crashFile: File? = null
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "specty-log").apply { isDaemon = true } }

    @Synchronized
    fun init(context: Context) {
        if (store != null) return
        val app = context.applicationContext
        store = LogStore(File(app.filesDir, "logs"))
        crashFile = File(app.filesDir, "logs/last_crash.txt")
        enabled = app.getSharedPreferences("specty", Context.MODE_PRIVATE).let {
            it.getBoolean(KEY_DEVELOPER, false) && it.getBoolean(KEY_LOGGING, false)
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                crashFile?.apply { parentFile?.mkdirs() }?.writeText(
                    LogStore.format(System.currentTimeMillis(), 'E', "Crash", "Uncaught in ${thread.name} (Specty ${BuildConfig.VERSION_NAME}, Android ${Build.VERSION.SDK_INT})", error),
                )
                if (enabled) store?.append(LogStore.format(System.currentTimeMillis(), 'E', "Crash", "Uncaught in ${thread.name}", error))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun d(tag: String, msg: () -> String) = log('D', tag, null, msg)
    fun i(tag: String, msg: () -> String) = log('I', tag, null, msg)
    fun w(tag: String, error: Throwable? = null, msg: () -> String) = log('W', tag, error, msg)
    fun e(tag: String, error: Throwable? = null, msg: () -> String) = log('E', tag, error, msg)

    private fun log(level: Char, tag: String, error: Throwable?, msg: () -> String) {
        if (!enabled) return
        val s = store ?: return
        val now = System.currentTimeMillis()
        val text = runCatching(msg).getOrDefault("(message failed)")
        writer.execute { runCatching { s.append(LogStore.format(now, level, tag, text, error)) } }
    }

    fun read(maxChars: Int = 100_000): String = store?.tail(maxChars).orEmpty()
    fun clear() { store?.clear(); writer.execute { } }
    fun lastCrash(): String? = crashFile?.takeIf { it.exists() }?.readText()
    fun clearCrash() { crashFile?.delete() }

    /** Facts that help with a bug report. */
    fun diagnostics(context: Context): String {
        val app = context.applicationContext
        fun size(f: File): Long = if (f.isFile) f.length() else f.listFiles()?.sumOf { size(it) } ?: 0L
        fun mb(b: Long) = "%.1f MB".format(java.util.Locale.US, b / 1_048_576.0)
        return buildString {
            appendLine("Specty ${BuildConfig.VERSION_NAME}")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Free storage: ${mb(app.filesDir.usableSpace)}")
            appendLine("App data: ${mb(size(app.filesDir))}, cache: ${mb(size(app.cacheDir))}")
            append("Max heap: ${Runtime.getRuntime().maxMemory() / 1_048_576} MB")
        }
    }
}
