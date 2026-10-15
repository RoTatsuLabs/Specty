package com.rotatsulabs.specty.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rotatsulabs.specty.audio.AnalysisResult
import com.rotatsulabs.specty.audio.AudioAnalyzer
import com.rotatsulabs.specty.audio.MediaProbe
import com.rotatsulabs.specty.audio.Verdict
import com.rotatsulabs.specty.audio.WindowFn
import com.rotatsulabs.specty.batch.BatchScanner
import com.rotatsulabs.specty.data.LibraryRepository
import com.rotatsulabs.specty.data.ResultCache
import com.rotatsulabs.specty.data.Track
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
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val analyzer = AudioAnalyzer(app)
    private val cache = ResultCache.get(app)
    private var lastUri: Uri? = null
    private val prefs = app.getSharedPreferences("specty", Context.MODE_PRIVATE)

    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysis: StateFlow<AnalysisState> = _analysis.asStateFlow()

    private val _viewing = MutableStateFlow(false)
    /** True while the full result screen is open; otherwise progress and results live in the dock. */
    val viewing: StateFlow<Boolean> = _viewing.asStateFlow()

    fun openResult() {
        if (_analysis.value is AnalysisState.Done) _viewing.value = true
    }

    private val _library = MutableStateFlow(LibraryState())
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
            val key = cache.keyOf(second, meta.sizeBytes, fft, _windowFn.value)
            if (meta.sizeBytes > 0) {
                val hit = withContext(Dispatchers.IO) { cache.load(key, second) }
                if (hit != null) {
                    _compare.value = CompareState.Ready(first, hit)
                    return@launch
                }
            }
            _compare.value = CompareState.Running(meta.displayName, "Reading file", 0f)
            try {
                val result = analyzer.analyze(second, fft, _windowFn.value) { stage, progress ->
                    _compare.value = CompareState.Running(meta.displayName, stage, progress)
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

    fun cacheKey(uri: Uri, sizeBytes: Long, fftSize: Int, window: WindowFn): String = cache.keyOf(uri, sizeBytes, fftSize, window)

    fun clearCache() = cache.clear()

    fun reanalyze() {
        lastUri?.let { analyze(it, force = true) }
    }

    fun analyze(uri: Uri, force: Boolean = false) {
        analysisJob?.cancel()
        lastUri = uri
        _viewing.value = false
        analysisJob = viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { MediaProbe.queryMeta(getApplication(), uri) }
            val title = meta.displayName
            val fft = _fftSize.value
            val cacheable = meta.sizeBytes > 0
            val key = cache.keyOf(uri, meta.sizeBytes, fft, _windowFn.value)

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
                if (cacheable) withContext(Dispatchers.IO) { cache.save(key, result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _analysis.value = AnalysisState.Failed(title, e.message ?: "Analysis failed")
            }
        }
    }

    fun closeAnalysis() {
        analysisJob?.cancel()
        _viewing.value = false
        _analysis.value = AnalysisState.Idle
    }

    fun loadLibrary(force: Boolean = false) {
        val current = _library.value
        if (current.loading || (current.loaded && !force)) return
        _library.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val tracks = LibraryRepository.scan(getApplication())
                _library.value = LibraryState(loaded = true, loading = false, tracks = tracks)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _library.value = LibraryState(loaded = true, loading = false, error = e.message ?: "Could not read the library")
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
    }
}
