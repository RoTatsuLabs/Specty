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
import com.rotatsulabs.specty.data.LibraryRepository
import com.rotatsulabs.specty.data.ResultCache
import com.rotatsulabs.specty.data.Track
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

data class LibraryState(
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val analyzer = AudioAnalyzer(app)
    private val cache = ResultCache(app)
    private var lastUri: Uri? = null
    private val prefs = app.getSharedPreferences("specty", Context.MODE_PRIVATE)

    private val _analysis = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysis: StateFlow<AnalysisState> = _analysis.asStateFlow()

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _fftSize = MutableStateFlow(prefs.getInt(KEY_FFT, 2048))
    val fftSize: StateFlow<Int> = _fftSize.asStateFlow()

    private var analysisJob: Job? = null

    fun setFftSize(size: Int) {
        _fftSize.value = size
        prefs.edit().putInt(KEY_FFT, size).apply()
    }

    /** Verdicts of files analysed before, keyed by [cacheKey]. */
    val cachedVerdicts: StateFlow<Map<String, Verdict>> = cache.verdicts

    fun cacheKey(uri: Uri, sizeBytes: Long, fftSize: Int): String = cache.keyOf(uri, sizeBytes, fftSize)

    fun clearCache() = cache.clear()

    fun reanalyze() {
        lastUri?.let { analyze(it, force = true) }
    }

    fun analyze(uri: Uri, force: Boolean = false) {
        analysisJob?.cancel()
        lastUri = uri
        analysisJob = viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { MediaProbe.queryMeta(getApplication(), uri) }
            val title = meta.displayName
            val fft = _fftSize.value
            val cacheable = meta.sizeBytes > 0
            val key = cache.keyOf(uri, meta.sizeBytes, fft)

            if (cacheable && !force) {
                val hit = withContext(Dispatchers.IO) { cache.load(key, uri) }
                if (hit != null) {
                    _analysis.value = AnalysisState.Done(hit, cached = true)
                    return@launch
                }
            }

            _analysis.value = AnalysisState.Running(title, "Reading file", 0f)
            try {
                val result = analyzer.analyze(uri, fft) { stage, progress ->
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
    }
}
