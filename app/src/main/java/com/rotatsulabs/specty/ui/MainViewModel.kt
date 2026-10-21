package com.rotatsulabs.specty.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rotatsulabs.specty.audio.AnalysisFailure
import com.rotatsulabs.specty.audio.AnalysisResult
import com.rotatsulabs.specty.audio.AnalysisStage
import com.rotatsulabs.specty.audio.AudioAnalyzer
import com.rotatsulabs.specty.audio.AudioInfo
import com.rotatsulabs.specty.audio.AuthenticityReport
import com.rotatsulabs.specty.audio.Failure
import com.rotatsulabs.specty.audio.LimitedAnalysisException
import com.rotatsulabs.specty.audio.MediaProbe
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.audio.WindowFn
import com.rotatsulabs.specty.batch.BatchScanner
import com.rotatsulabs.specty.data.LibraryFolder
import com.rotatsulabs.specty.data.LibraryFolders
import com.rotatsulabs.specty.data.LibraryRepository
import com.rotatsulabs.specty.data.ResultCache
import com.rotatsulabs.specty.data.Track
import com.rotatsulabs.specty.ui.design.tonal.SeedPalette
import com.rotatsulabs.specty.ui.theme.Appearance
import com.rotatsulabs.specty.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import com.rotatsulabs.specty.dev.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Running(val title: String, val stage: AnalysisStage, val progress: Float) : AnalysisState
    data class Done(val result: AnalysisResult, val cached: Boolean = false) : AnalysisState
    /** [failure] is set for problems the app words itself; otherwise [detail] is the system's own message, if any. */
    data class Failed(val title: String, val failure: Failure?, val detail: String?) : AnalysisState
    /** The file was read, but its audio cannot be decoded here, so only what the container shows is reported. */
    data class Limited(val title: String, val info: AudioInfo, val report: AuthenticityReport) : AnalysisState
}

sealed interface CompareState {
    data object Idle : CompareState
    data class Running(val title: String, val stage: AnalysisStage, val progress: Float) : CompareState
    data class Ready(val a: AnalysisResult, val b: AnalysisResult) : CompareState
    data class Failed(val failure: Failure?, val detail: String?) : CompareState
}

data class LibraryState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
    /** The folders the library is built from. Empty until the person picks one. */
    val folders: List<LibraryFolder> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val analyzer = AudioAnalyzer(app)
    private val cache = ResultCache.get(app)
    private var lastUri: Uri? = null
    private val prefs = app.getSharedPreferences("specty", Context.MODE_PRIVATE)

    init {
        viewModelScope.launch(Dispatchers.IO) { AudioAnalyzer.sweepStale(app) }
    }

    private val _developer = MutableStateFlow(prefs.getBoolean(AppLog.KEY_DEVELOPER, false))
    val developer: StateFlow<Boolean> = _developer.asStateFlow()
    private val _logging = MutableStateFlow(prefs.getBoolean(AppLog.KEY_DEVELOPER, false) && prefs.getBoolean(AppLog.KEY_LOGGING, false))
    val logging: StateFlow<Boolean> = _logging.asStateFlow()

    fun setDeveloper(enabled: Boolean) {
        _developer.value = enabled
        prefs.edit().putBoolean(AppLog.KEY_DEVELOPER, enabled).apply()
        if (!enabled) setLogging(false)
    }

    fun setLogging(enabled: Boolean) {
        if (enabled) AppLog.enabled = true
        AppLog.i("Dev") { "Logging ${if (enabled) "on" else "off"}" }
        _logging.value = enabled
        AppLog.enabled = enabled
        prefs.edit().putBoolean(AppLog.KEY_LOGGING, enabled).apply()
    }

    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysis: StateFlow<AnalysisState> = _analysis.asStateFlow()

    private val _viewing = MutableStateFlow(false)
    /** True while the full result screen is open; otherwise progress and results live in the dock. */
    val viewing: StateFlow<Boolean> = _viewing.asStateFlow()

    fun openResult() {
        if (_analysis.value.let { it is AnalysisState.Done || it is AnalysisState.Limited }) _viewing.value = true
    }

    private val folderStore = LibraryFolders(app)
    private var libraryJob: Job? = null
    private val _library = MutableStateFlow(LibraryState(folders = folderStore.list()))
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _fftSize = MutableStateFlow(prefs.getInt(KEY_FFT, 2048))
    val fftSize: StateFlow<Int> = _fftSize.asStateFlow()

    private val _windowFn = MutableStateFlow(WindowFn.entries.getOrElse(prefs.getInt(KEY_WINDOW, 0)) { WindowFn.Hann })
    val windowFn: StateFlow<WindowFn> = _windowFn.asStateFlow()

    fun setWindowFn(w: WindowFn) {
        _windowFn.value = w
        prefs.edit().putInt(KEY_WINDOW, w.ordinal).apply()
    }

    private val _appearance = MutableStateFlow(
        Appearance(
            mode = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, 0)) { ThemeMode.System },
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
            pureBlack = prefs.getBoolean(KEY_BLACK, false),
            seed = SeedPalette.entries.getOrElse(prefs.getInt(KEY_SEED, 0)) { SeedPalette.Violet },
        ),
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _appearance.value = _appearance.value.copy(mode = mode)
        prefs.edit().putInt(KEY_THEME, mode.ordinal).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _appearance.value = _appearance.value.copy(dynamicColor = enabled)
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun setSeed(seed: SeedPalette) {
        _appearance.value = _appearance.value.copy(seed = seed)
        prefs.edit().putInt(KEY_SEED, seed.ordinal).apply()
    }

    fun setPureBlack(enabled: Boolean) {
        _appearance.value = _appearance.value.copy(pureBlack = enabled)
        prefs.edit().putBoolean(KEY_BLACK, enabled).apply()
    }

    private var analysisJob: Job? = null

    private val _onboarding = MutableStateFlow(!prefs.getBoolean(KEY_ONBOARDED, false))
    /** True on first launch and when replayed from settings. */
    val onboarding: StateFlow<Boolean> = _onboarding.asStateFlow()

    fun finishOnboarding() {
        _onboarding.value = false
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun replayOnboarding() {
        _onboarding.value = true
    }

    private val _compare = MutableStateFlow<CompareState>(CompareState.Idle)
    val compare: StateFlow<CompareState> = _compare.asStateFlow()
    private var compareJob: Job? = null

    /** The saved result for [uri] when there is one, otherwise a fresh analysis that is saved for next time. */
    private suspend fun savedOrAnalysed(uri: Uri, onStage: (title: String, stage: AnalysisStage, progress: Float) -> Unit): AnalysisResult {
        val meta = withContext(Dispatchers.IO) { MediaProbe.queryMeta(getApplication(), uri) }
        val fft = _fftSize.value
        val key = cache.keyOf(uri, meta.sizeBytes, meta.modified, fft, _windowFn.value)
        if (meta.sizeBytes > 0) {
            withContext(Dispatchers.IO) { cache.load(key, uri) }?.let { return it }
        }
        val title = titleOf(uri, meta.displayName)
        val result = analyzer.analyze(uri, fft, _windowFn.value) { stage, progress -> onStage(title, stage, progress) }
        if (meta.sizeBytes > 0) withContext(Dispatchers.IO) { cache.save(key, result) }
        return result
    }

    private fun runCompare(load: suspend () -> Pair<AnalysisResult, AnalysisResult>) {
        compareJob?.cancel()
        compareJob = viewModelScope.launch {
            try {
                val (a, b) = load()
                _compare.value = CompareState.Ready(a, b)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _compare.value = CompareState.Failed((e as? AnalysisFailure)?.failure, e.message)
            }
        }
    }

    private val showProgress = { title: String, stage: AnalysisStage, progress: Float ->
        _compare.value = CompareState.Running(title, stage, progress)
    }

    /** Analyses [second] (or loads it from the cache) and shows it next to the result currently open. */
    fun startCompare(second: Uri) {
        val first = (_analysis.value as? AnalysisState.Done)?.result ?: return
        runCompare { first to savedOrAnalysed(second, showProgress) }
    }

    /** Shows two library tracks side by side, analysing whichever has no saved result yet. */
    fun compareTracks(first: Uri, second: Uri) {
        runCompare { savedOrAnalysed(first, showProgress) to savedOrAnalysed(second, showProgress) }
    }

    fun closeCompare() {
        compareJob?.cancel()
        _compare.value = CompareState.Idle
    }

    /** Progress of the background library scan. */
    val batch: StateFlow<BatchScanner.State> = BatchScanner.state

    /** Queues the tracks with no saved result: new files, and files whose size or date changed since they were scanned. */
    fun scanLibrary() {
        val fft = _fftSize.value
        val window = _windowFn.value
        val todo = _library.value.tracks.filter { !(it.sizeBytes > 0 && cache.contains(cache.keyOf(it.uri, it.sizeBytes, it.modifiedSec, fft, window))) }
        BatchScanner.start(getApplication(), todo, fft, window)
    }

    fun cancelScan() = BatchScanner.cancel(getApplication())

    fun dismissScanSummary() = BatchScanner.dismissSummary()

    fun setFftSize(size: Int) {
        _fftSize.value = size
        prefs.edit().putInt(KEY_FFT, size).apply()
    }

    /** Verdicts of files analysed before, keyed by [cacheKey]. */
    val cachedVerdicts: StateFlow<Map<String, Verdict>> = cache.verdicts

    fun cacheKey(uri: Uri, sizeBytes: Long, modified: Long, fftSize: Int, window: WindowFn): String =
        cache.keyOf(uri, sizeBytes, modified, fftSize, window)

    fun clearCache() = cache.clear()

    fun reanalyze() {
        lastUri?.let { analyze(it, force = true) }
    }

    /** Analyses [uri] now, replacing any analysis still running. */
    fun analyze(uri: Uri, force: Boolean = false) = start(uri, force)

    /** The song title the library already shows, so a running analysis is not named after its file. */
    private fun titleOf(uri: Uri, fileName: String): String =
        _library.value.tracks.firstOrNull { it.uri == uri }?.title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.', fileName)

    private fun start(uri: Uri, force: Boolean) {
        analysisJob?.cancel()
        lastUri = uri
        _viewing.value = false
        analysisJob = viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { MediaProbe.queryMeta(getApplication(), uri) }
            val title = titleOf(uri, meta.displayName)
            val fft = _fftSize.value
            val cacheable = meta.sizeBytes > 0
            val key = cache.keyOf(uri, meta.sizeBytes, meta.modified, fft, _windowFn.value)

            if (cacheable && !force) {
                val hit = withContext(Dispatchers.IO) { cache.load(key, uri) }
                if (hit != null) {
                    _analysis.value = AnalysisState.Done(hit, cached = true)
                    _viewing.value = true
                    return@launch
                }
            }

            _analysis.value = AnalysisState.Running(title, AnalysisStage.READING, 0f)
            val began = System.currentTimeMillis()
            AppLog.i("Analyze") { "Start \"$title\" (${meta.sizeBytes / 1024} KB, FFT $fft)" }
            try {
                val result = analyzer.analyze(uri, fft, _windowFn.value) { stage, progress ->
                    _analysis.value = AnalysisState.Running(title, stage, progress)
                }
                _analysis.value = AnalysisState.Done(result)
                AppLog.i("Analyze") { "Done \"$title\" in ${System.currentTimeMillis() - began} ms: ${result.authenticity.verdict}" }
                if (cacheable) withContext(Dispatchers.IO) { cache.save(key, result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LimitedAnalysisException) {
                AppLog.w("Analyze") { "Limited \"$title\": ${e.report.verdict}" }
                _analysis.value = AnalysisState.Limited(title, e.info, e.report)
            } catch (e: Throwable) {
                AppLog.e("Analyze", e) { "Failed \"$title\" after ${System.currentTimeMillis() - began} ms" }
                _analysis.value = AnalysisState.Failed(title, (e as? AnalysisFailure)?.failure, e.message)
            }
        }
    }

    fun closeAnalysis() {
        analysisJob?.cancel()
        _viewing.value = false
        _analysis.value = AnalysisState.Idle
    }

    /** Adds a folder picked with the system picker and keeps access to it across restarts. */
    fun addFolder(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        folderStore.add(uri)
        _library.value = _library.value.copy(folders = folderStore.list())
        loadLibrary(force = true)
    }

    fun removeFolder(uri: Uri) {
        runCatching {
            getApplication<Application>().contentResolver
                .releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        folderStore.remove(uri)
        _library.value = _library.value.copy(folders = folderStore.list())
        loadLibrary(force = true)
    }

    fun loadLibrary(force: Boolean = false) {
        val current = _library.value
        if (!force && (current.loading || current.loaded)) return
        libraryJob?.cancel()
        val folders = folderStore.list()
        if (folders.isEmpty()) {
            _library.value = LibraryState(loaded = true, folders = folders)
            return
        }
        _library.value = current.copy(loading = true, error = null, folders = folders)
        libraryJob = viewModelScope.launch {
            try {
                val scanBegan = System.currentTimeMillis()
                val tracks = LibraryRepository.scan(getApplication(), folders.map { it.uri }) { partial ->
                    _library.value = _library.value.copy(tracks = partial)
                }
                AppLog.i("Library") { "Scanned ${tracks.size} tracks in ${folders.size} folders, ${System.currentTimeMillis() - scanBegan} ms" }
                _library.value = LibraryState(loaded = true, loading = false, tracks = tracks, folders = folders)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.e("Library", e) { "Library scan failed" }
                _library.value = LibraryState(
                    loaded = true, loading = false, error = e.message.orEmpty(), folders = folders,
                )
            }
        }
    }

    private companion object {
        const val KEY_FFT = "fft_size"
        const val KEY_WINDOW = "window_fn"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_BLACK = "pure_black"
        const val KEY_SEED = "seed_palette"
    }
}
