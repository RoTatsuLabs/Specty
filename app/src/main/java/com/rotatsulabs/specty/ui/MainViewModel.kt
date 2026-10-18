package com.rotatsulabs.specty.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rotatsulabs.specty.audio.AnalysisResult
import com.rotatsulabs.specty.audio.AudioAnalyzer
import com.rotatsulabs.specty.audio.AudioInfo
import com.rotatsulabs.specty.audio.AuthenticityReport
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
import com.rotatsulabs.specty.sound.Fx
import com.rotatsulabs.specty.sound.SoundFx
import com.rotatsulabs.specty.ui.design.tonal.SeedPalette
import com.rotatsulabs.specty.ui.theme.Appearance
import com.rotatsulabs.specty.ui.theme.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AnalysisState {
    data object Idle : AnalysisState
    data class Running(val title: String, val stage: String, val progress: Float) : AnalysisState
    data class Done(val result: AnalysisResult, val cached: Boolean = false) : AnalysisState
    data class Failed(val title: String, val message: String) : AnalysisState
    /** The file was read, but its audio cannot be decoded here, so only what the container shows is reported. */
    data class Limited(val title: String, val info: AudioInfo, val report: AuthenticityReport) : AnalysisState
}

sealed interface CompareState {
    data object Idle : CompareState
    data class Running(val title: String, val stage: String, val progress: Float) : CompareState
    data class Ready(val a: AnalysisResult, val b: AnalysisResult) : CompareState
    data class Failed(val message: String) : CompareState
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
        SoundFx.enabled = prefs.getBoolean(KEY_SOUND, true)
        SoundFx.warmUp()
    }

    private val _sound = MutableStateFlow(SoundFx.enabled)
    val sound: StateFlow<Boolean> = _sound.asStateFlow()

    fun setSound(enabled: Boolean) {
        _sound.value = enabled
        SoundFx.enabled = enabled
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        if (enabled) SoundFx.play(Fx.Tap)
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

    private val _showMetadata = MutableStateFlow(prefs.getBoolean(KEY_META, false))
    val showMetadata: StateFlow<Boolean> = _showMetadata.asStateFlow()

    fun setShowMetadata(enabled: Boolean) {
        _showMetadata.value = enabled
        prefs.edit().putBoolean(KEY_META, enabled).apply()
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

    /** Analyses [second] (or loads it from the cache) and shows it next to the result currently open. */
    fun startCompare(second: Uri) {
        val first = (_analysis.value as? AnalysisState.Done)?.result ?: return
        compareJob?.cancel()
        compareJob = viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { MediaProbe.queryMeta(getApplication(), second) }
            val fft = _fftSize.value
            val key = cache.keyOf(second, meta.sizeBytes, meta.modified, fft, _windowFn.value)
            if (meta.sizeBytes > 0) {
                val hit = withContext(Dispatchers.IO) { cache.load(key, second) }
                if (hit != null) {
                    _compare.value = CompareState.Ready(first, hit)
                    return@launch
                }
            }
            _compare.value = CompareState.Running(titleOf(second, meta.displayName), "Reading file", 0f)
            try {
                val result = analyzer.analyze(second, fft, _windowFn.value) { stage, progress ->
                    _compare.value = CompareState.Running(titleOf(second, meta.displayName), stage, progress)
                }
                if (meta.sizeBytes > 0) withContext(Dispatchers.IO) { cache.save(key, result) }
                _compare.value = CompareState.Ready(first, result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _compare.value = CompareState.Failed(e.message ?: "Analysis failed")
            }
        }
    }

    fun closeCompare() {
        compareJob?.cancel()
        _compare.value = CompareState.Idle
    }

    /** Progress of the background library scan. */
    val batch: StateFlow<BatchScanner.State> = BatchScanner.state

    fun scanLibrary() {
        BatchScanner.start(getApplication(), _library.value.tracks, _fftSize.value, _windowFn.value)
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

    private val waiting = ArrayDeque<Uri>()
    private val _queued = MutableStateFlow(0)
    /** Files waiting for the running analysis to finish. */
    val queued: StateFlow<Int> = _queued.asStateFlow()

    /** True while an analysis is running, so another file would join the queue instead of replacing it. */
    val busy: Boolean get() = analysisJob?.isActive == true && _analysis.value is AnalysisState.Running

    private fun enqueue(uri: Uri) {
        val message = if (uri == lastUri || uri in waiting) {
            "Already in the queue"
        } else {
            waiting.addLast(uri)
            _queued.value = waiting.size
            SoundFx.play(Fx.Queue)
            "Added to the queue"
        }
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }

    /** Starts the next waiting file, unless a result is open on screen (it starts once that closes). */
    private fun advance() {
        if (_viewing.value) return
        val next = waiting.removeFirstOrNull() ?: return
        _queued.value = waiting.size
        start(next, force = false)
    }

    /** Analyses [uri] now, or queues it when another analysis is still running. */
    fun analyze(uri: Uri, force: Boolean = false) {
        if (!force && busy) {
            enqueue(uri)
            return
        }
        start(uri, force)
    }

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

            _analysis.value = AnalysisState.Running(title, "Reading file", 0f)
            try {
                val result = analyzer.analyze(uri, fft, _windowFn.value) { stage, progress ->
                    _analysis.value = AnalysisState.Running(title, stage, progress)
                }
                _analysis.value = AnalysisState.Done(result)
                SoundFx.play(Fx.Done)
                if (cacheable) withContext(Dispatchers.IO) { cache.save(key, result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LimitedAnalysisException) {
                _analysis.value = AnalysisState.Limited(title, e.info, e.report)
                SoundFx.play(Fx.Done)
            } catch (e: Throwable) {
                _analysis.value = AnalysisState.Failed(title, e.message ?: "Analysis failed")
                SoundFx.play(Fx.Fail)
            }
            advance()
        }
    }

    fun closeAnalysis() {
        // Cancelling a running analysis stops the whole queue; closing a finished result lets the queue go on.
        if (_analysis.value is AnalysisState.Running) {
            waiting.clear()
            _queued.value = 0
        }
        analysisJob?.cancel()
        _viewing.value = false
        _analysis.value = AnalysisState.Idle
        advance()
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
                val tracks = LibraryRepository.scan(getApplication(), folders.map { it.uri }) { partial ->
                    _library.value = _library.value.copy(tracks = partial)
                }
                _library.value = LibraryState(loaded = true, loading = false, tracks = tracks, folders = folders)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _library.value = LibraryState(
                    loaded = true, loading = false, error = e.message ?: "Could not read the library", folders = folders,
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
        const val KEY_META = "show_metadata"
        const val KEY_SOUND = "sound_fx"
    }
}
